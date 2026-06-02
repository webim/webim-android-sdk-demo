package ru.webim.android.sdk.impl.backend;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit2.Call;
import ru.webim.android.sdk.BuildConfig;
import ru.webim.android.sdk.ProvidedAuthorizationTokenStateListener;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.WebimSession;
import ru.webim.android.sdk.impl.InternalUtils;
import ru.webim.android.sdk.impl.WebimErrorImpl;
import ru.webim.android.sdk.impl.backend.callbacks.DeltaCallback;
import ru.webim.android.sdk.impl.items.delta.DeltaFullUpdate;
import ru.webim.android.sdk.impl.items.delta.DeltaItem;
import ru.webim.android.sdk.impl.items.responses.AuthResponse;
import ru.webim.android.sdk.impl.items.responses.DeltaResponse;
import ru.webim.android.sdk.impl.items.responses.MetaResponse;
import ru.webim.android.sdk.impl.items.responses.ServerConfigsResponse;

public class DeltaRequestLoop extends AbstractRequestLoop {
    public static final String INCORRECT_SERVER_ANSWER = "Incorrect server answer";
    private static int providedAuthTokenErrorCount = 0;
    private final @Nullable String appVersion;
    private final @NonNull
    DeltaCallback callback;
    private final @NonNull String deviceId;
    private final @NonNull String platform;
    private final @Nullable String prechatFields;
    private final @Nullable SessionParamsListener sessionParamsListener;
    private final @NonNull String title;
    private final @Nullable String visitorFieldsJson;
    private final @NonNull WebimService webim;
    private @Nullable Webim.PushSystem pushSystem;
    private volatile @Nullable String pushToken;
    private volatile @Nullable AuthData authData;
    private volatile @NonNull String location;
    private @Nullable ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener;
    private @Nullable String providedAuthorizationToken;
    private @Nullable String visitorJson;
    private @Nullable String sessionId;
    private @Nullable WebimSession.SessionCallback sessionCallback;
    private final @NonNull ServerConfigsCallback serverConfigsCallback;
    private final @NonNull MetaUrlProvider metaUrlProvider;
    private volatile String since = DEFAULT_SINCE;
    private boolean accountConfigCalled = false;
    private boolean metaRequestCalled = false;
    private final static String DEFAULT_SINCE = "0";
    private final boolean isLightModeEnabled;
    private volatile boolean reinitRequired = false;

    public DeltaRequestLoop(
        @NonNull DeltaCallback callback,
        @Nullable SessionParamsListener sessionParamsListener,
        @NonNull Executor callbackExecutor,
        @NonNull InternalErrorListener errorListener,
        @NonNull WebimService webim,
        @NonNull String platform,
        @NonNull String title,
        @NonNull String location,
        @Nullable String appVersion,
        @Nullable String visitorFieldsJson,
        @Nullable ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener,
        @Nullable String providedAuthorizationToken,
        @NonNull String deviceId,
        @Nullable String prechatFields,
        @Nullable WebimSession.SessionCallback sessionCallback,
        @NonNull ServerConfigsCallback serverConfigsCallback,
        boolean isLightModeEnabled,
        @NonNull MetaUrlProvider metaUrlProvider
    ) {
        super(callbackExecutor, errorListener);
        this.callback = callback;
        this.sessionParamsListener = sessionParamsListener;
        this.webim = webim;
        this.platform = platform;
        this.title = title;
        this.location = location;
        this.appVersion = appVersion;
        this.visitorFieldsJson = visitorFieldsJson;
        this.providedAuthorizationTokenStateListener = providedAuthorizationTokenStateListener;
        this.providedAuthorizationToken = providedAuthorizationToken;
        this.deviceId = deviceId;
        this.prechatFields = prechatFields;
        this.sessionCallback = sessionCallback;
        this.serverConfigsCallback = serverConfigsCallback;
        this.isLightModeEnabled = isLightModeEnabled;
        this.metaUrlProvider = metaUrlProvider;
    }

    public DeltaRequestLoop(
        @NonNull DeltaCallback callback,
        @Nullable SessionParamsListener sessionParamsListener,
        @NonNull Executor callbackExecutor,
        @NonNull InternalErrorListener errorListener,
        @NonNull WebimService webim,
        @NonNull String platform,
        @NonNull String title,
        @NonNull String location,
        @NonNull String appVersion,
        @Nullable String visitorFieldsJson,
        @Nullable ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener,
        @Nullable String providedAuthorizationToken,
        @NonNull String deviceId,
        @Nullable String prechatFields,
        @Nullable Webim.PushSystem pushSystem,
        @Nullable String pushToken,
        @Nullable String visitorJson,
        @Nullable String sessionId,
        @Nullable AuthData authData,
        @Nullable WebimSession.SessionCallback sessionCallback,
        @NonNull ServerConfigsCallback serverConfigsCallback,
        boolean isLightModeEnabled,
        @NonNull MetaUrlProvider metaUrlProvider
    ) {
        this(
            callback,
            sessionParamsListener,
            callbackExecutor,
            errorListener,
            webim,
            platform,
            title,
            location,
            appVersion,
            visitorFieldsJson,
            providedAuthorizationTokenStateListener,
            providedAuthorizationToken,
            deviceId,
            prechatFields,
            sessionCallback,
            serverConfigsCallback,
            isLightModeEnabled,
            metaUrlProvider
        );

        this.pushSystem = pushSystem;
        this.pushToken = pushToken;
        this.visitorJson = visitorJson;
        this.sessionId = sessionId;
        this.authData = authData;
    }

    @Nullable
    public AuthData getAuthData() {
        return this.authData;
    }

    public void setPushToken(@Nullable String pushToken) {
        this.pushToken = pushToken;
    }

    public void changeLocation(@NonNull String location) {
        synchronized (this) {
            this.location = location;
            authData = null;
            since = DEFAULT_SINCE;
            accountConfigCalled = false;
            metaRequestCalled = false;
            reinitRequired = true;
        }

        cancelRequest();
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    protected void run() {
        android.util.Log.d("WebimLoop_DEBUG", ">>> STARTED: " + location +
                " / pageId=" + (authData != null ? authData.getPageId() : "null") +
                " / thread=" + Thread.currentThread().getId());

        try {
            while (isRunning()) {
                try {
                    synchronized (this) {
                        if (reinitRequired) {
                            reinitRequired = false;
                        }
                    }
                    runIteration();
                } catch (final AbortByWebimErrorException exception) {
                    if (WebimInternalError.REINIT_REQUIRED.equals(exception.getError())) {
                        authData = null;
                        since = DEFAULT_SINCE;
                    } else if (WebimInternalError.PROVIDED_AUTHORIZATION_TOKEN_NOT_FOUND
                            .equals(exception.getError())) {
                        handleProvidedAuthorizationTokenError();
                    } else if (WebimInternalError.UNSUPPORTED_URL.equals(exception.getError())) {
                        android.util.Log.w("WebimDemo", "Endpoint not supported: " + exception.getRequest().request().url());
                        return;
                    } else {
                        running = false;
                        callbackExecutor.execute(() -> errorListener.onError(
                                exception.getRequest().request().url().toString(),
                                exception.getError(),
                                exception.getHttpCode()));
                    }
                } catch (InterruptedRuntimeException ignored) {
                    Log.w("DeltaRequestLoop", "Caught InterruptedRuntimeException - loop will restart or exit cleanly");
                }
            }
        } catch (final Throwable t) {
            Log.e("DeltaRequestLoop", "Critical error in DeltaRequestLoop run()", t);
            running = false;
            throw t;
        } finally {
            android.util.Log.d("WebimLoop_DEBUG", "<<< FINISHED: " + location +
                    " / pageId=" + (authData != null ? authData.getPageId() : "null"));
        }
    }

    private void runIteration() {
        try {
            while (isRunning() && !reinitRequired) {
                try {
                    if ((authData != null) && (!since.equals(DEFAULT_SINCE))) {
                        if (!metaRequestCalled) {
                            requestMetaConfig();
                        } else if (!accountConfigCalled) {
                            requestAccountConfig();
                        } else {
                            requestDelta();
                        }
                    } else {
                        requestInit();
                    }
                    break;
                } catch (InterruptedIOException | FileNotFoundException ignored) {
                    Log.e("DeltaRequestLoop", "runIteration() " + String.valueOf(ignored));
                    if (reinitRequired) {
                        return;
                    }
                }
            }
        } catch (final AbortByWebimErrorException exception) {
            throw exception;
        }
    }

    private void requestInit() throws InterruptedIOException, FileNotFoundException {
        if (isLightModeEnabled) {
            Call<AuthResponse> authResponseCall = makeAuthRequest();
            final AuthResponse response = performRequest(authResponseCall);
            String authToken = response.getAuthToken();
            String visitorJson = response.getVisitor();
            final AuthData authData = new AuthData("", authToken);
            this.authData = authData;

            if (sessionParamsListener != null) {
                callbackExecutor.execute(() -> {
                    sessionParamsListener.onSessionParamsChanged(visitorJson, "", authData);
                });
            }
            // Stop delta loop because of light mode not support long poll
            running = false;
            return;
        }

        try {
            final Call<DeltaResponse> request = makeInitRequest();
            final DeltaResponse delta = performRequest(request);

            if ((delta.getDeltaList() != null) && (delta.getDeltaList().size() != 0)
                    || (delta.getFullUpdate() == null)) {
                callbackExecutor.execute(() -> {
                    if (sessionCallback != null) {
                        sessionCallback.onFailure(new WebimErrorImpl<>(
                            WebimSession.SessionCallback.SessionError.REQUEST_ERROR,
                            null
                        ));
                    }
                    errorListener.onError(
                        request.request().url().toString(),
                        INCORRECT_SERVER_ANSWER,
                        200
                    );
                });

                return;
            }

            String revision = delta.getRevision();
            if (revision != null) {
                since = delta.getRevision();
            }

            callbackExecutor.execute(() -> {
                if (sessionCallback != null) {
                    sessionCallback.onSuccess();
                }
            });
            processFullUpdate(delta.getFullUpdate());
        } catch (Exception exception) {
            callbackExecutor.execute(() -> {
                if (sessionCallback != null) {
                    sessionCallback.onFailure(new WebimErrorImpl<>(
                            WebimSession.SessionCallback.SessionError.REQUEST_ERROR,
                            null)
                    );
                }
            });
            throw exception;
        }
    }

    private void requestDelta() throws InterruptedIOException, FileNotFoundException {
        try {
            DeltaResponse delta = performRequest(makeDeltaRequest());

            if (delta.getRevision() == null) {
                return; // Delta timeout.
            }

            since = delta.getRevision();

            if (delta.getFullUpdate() != null) {
                processFullUpdate(delta.getFullUpdate());
            } else {
                final List<DeltaItem<?>> list = delta.getDeltaList();
                if (list != null && list.size() != 0) {
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDeltaList(list);
                        }
                    });
                }
            }
        } catch (final AbortByWebimErrorException exception) {
            throw exception;
        }
    }

    private void requestMetaConfig() throws InterruptedIOException, FileNotFoundException {
        try {
            Call<MetaResponse> metaRequest = webim.getMeta();
            MetaResponse response = performRequest(metaRequest);
            if (response != null) {
                callbackExecutor.execute(() -> metaUrlProvider.setMetaResponse(response));
            }
        } catch (final AbortByWebimErrorException ignored) {
        } finally {
            metaRequestCalled = true;
        }
    }

    private void requestAccountConfig() throws InterruptedIOException, FileNotFoundException {
        try {
            Call<ServerConfigsResponse> accountConfigRequest = webim.getAccountConfig(metaUrlProvider.getConfigsUrl(), location);
            ServerConfigsResponse response = performRequest(accountConfigRequest);
            if (response != null) {
                callbackExecutor.execute(() -> serverConfigsCallback.onServerConfigs(response.getAccountConfig(), response.getLocationSettings()));
            }
        } catch (final AbortByWebimErrorException ignored) {
            serverConfigsCallback.onServerConfigs(null, null);
        } finally {
            accountConfigCalled = true;
        }
    }

    private Call<DeltaResponse> makeInitRequest() {
        return webim.getLogin(
                BuildConfig.VERSION_NAME,
                "init",
                pushSystem.getPushName(),
                pushToken,
                platform,
                visitorFieldsJson,
                visitorJson,
                providedAuthorizationToken,
                location,
                appVersion,
                sessionId,
                title,
                0,
                true,
                deviceId,
                prechatFields
        );
    }

    private Call<AuthResponse> makeAuthRequest() {
        return webim.getAuth(
            BuildConfig.VERSION_NAME,
            pushSystem.getPushName(),
            pushToken,
            platform,
            visitorFieldsJson,
            visitorJson,
            providedAuthorizationToken,
            appVersion,
            deviceId
        );
    }

    private Call<DeltaResponse> makeDeltaRequest() {
        return webim.getDelta(
                since,
                authData == null ? null : authData.getPageId(),
                authData == null ? null : authData.getAuthToken(),
                System.currentTimeMillis()
        );
    }

    private void processFullUpdate(final DeltaFullUpdate fullUpdate) {
        if ((visitorJson == null)
                || !visitorJson.equals(fullUpdate.getVisitorJson())
                || (sessionId == null)
                || !sessionId.equals(fullUpdate.getVisitSessionId())
                || (authData == null)
                || !authData.getPageId().equals(fullUpdate.getPageId())
                || !InternalUtils.equals(authData.getAuthToken(), fullUpdate.getAuthToken())) {
            final String visitorJsonString = fullUpdate.getVisitorJson();
            final String visitSessionId = fullUpdate.getVisitSessionId();
            String pageId = fullUpdate.getPageId();
            String authToken = fullUpdate.getAuthToken();

            this.visitorJson = visitorJsonString;
            this.sessionId = visitSessionId;

            if (pageId != null) {
                final AuthData authData = new AuthData(pageId, authToken);
                this.authData = authData;

                if (sessionParamsListener != null && visitorJsonString != null && visitSessionId != null) {
                    callbackExecutor.execute(() -> sessionParamsListener.onSessionParamsChanged(visitorJsonString, visitSessionId, authData));
                }
            }
        }

        callbackExecutor.execute(() -> callback.onFullUpdate(fullUpdate));
    }

    private void handleProvidedAuthorizationTokenError() {
        providedAuthTokenErrorCount += 1;

        if (providedAuthTokenErrorCount < 5) {
            sleepBetweenInitializationAttempts();
        } else {
            if (providedAuthorizationTokenStateListener != null) {
                providedAuthorizationTokenStateListener
                        .updateProvidedAuthorizationToken(
                                providedAuthorizationToken
                        );
            }

            providedAuthTokenErrorCount = 0;

            sleepBetweenInitializationAttempts();
        }
    }

    private void sleepBetweenInitializationAttempts() {
        authData = null;
        since = DEFAULT_SINCE;

        try {
            for (int i = 0; i < 10 && isRunning(); i++) {
                Thread.sleep(100);
            }
        } catch (InterruptedException ignored) { }
    }
}
