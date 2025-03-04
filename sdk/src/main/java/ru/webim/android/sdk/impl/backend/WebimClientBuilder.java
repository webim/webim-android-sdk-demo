package ru.webim.android.sdk.impl.backend;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.webim.android.sdk.BuildConfig;
import ru.webim.android.sdk.NotFatalErrorHandler;
import ru.webim.android.sdk.ProvidedAuthorizationTokenStateListener;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.WebimSession;
import ru.webim.android.sdk.impl.backend.callbacks.DeltaCallback;
import ru.webim.android.sdk.impl.items.delta.DeltaFullUpdate;
import ru.webim.android.sdk.impl.items.delta.DeltaItem;

public class WebimClientBuilder {

    private static final int ACTION_TIMEOUT_READ_IN_SECONDS = 60;
    private static final int DELTA_TIMEOUT_READ_IN_SECONDS = 44;
    private static final int TIMEOUT_WRITE_IN_SECONDS = 60;
    private static final int TIMEOUT_CALL_IN_SECONDS = 60;
    private static final String USER_AGENT_FORMAT = "Android: Webim-Client/%s (%s; Android %s)";
    private static final String USER_AGENT_STRING
            = String.format(USER_AGENT_FORMAT, BuildConfig.VERSION_NAME,
            Build.MODEL, Build.VERSION.RELEASE);
    private String appVersion;
    private AuthData authData;
    private String baseUrl;
    private Executor callbackExecutor;
    private DeltaCallback deltaCallback;
    private String deviceId;
    private InternalErrorListener errorListener;
    private String location;
    private String platform;
    private String providedAuthorizationToken;
    private ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener;
    private Webim.PushSystem pushSystem;
    private String pushToken;
    private String sessionId;
    private SessionParamsListener sessionParamsListener;
    private String title;
    private String visitorFieldsJson;
    private String visitorJson;
    private String prechatFields;
    private SSLSocketFactory sslSocketFactory;
    private X509TrustManager trustManager;
    private WebimSession.SessionCallback sessionCallback;
    private ServerConfigsCallback serverConfigsCallback;
    private Map<String, String> requestHeader;
    private List<String> extraDomains;

    public WebimClientBuilder() {
    }

    public WebimClientBuilder setBaseUrl(@NonNull String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public WebimClientBuilder setLocation(@NonNull String location) {
        this.location = location;
        return this;
    }

    public WebimClientBuilder setAppVersion(@Nullable String appVersion) {
        this.appVersion = appVersion;
        return this;
    }

    public WebimClientBuilder setVisitorFieldsJson(@Nullable String visitorFieldsJson) {
        this.visitorFieldsJson = visitorFieldsJson;
        return this;
    }

    public WebimClientBuilder setPushToken(
            @NonNull Webim.PushSystem pushSystem,
            @Nullable String pushToken) {
        this.pushSystem = pushSystem;
        this.pushToken = pushToken;
        return this;
    }

    public WebimClientBuilder setVisitorJson(@Nullable String visitorJson) {
        this.visitorJson = visitorJson;
        return this;
    }

    public WebimClientBuilder setExtraDomains(List<String> extraDomains) {
        this.extraDomains = extraDomains;
        return this;
    }

    public WebimClientBuilder setProvidedAuthorizationListener(
            @Nullable ProvidedAuthorizationTokenStateListener providedAuthorizationTokenStateListener
    ) {
        this.providedAuthorizationTokenStateListener = providedAuthorizationTokenStateListener;
        return this;
    }

    public WebimClientBuilder setProvidedAuthorizationToken(
            @Nullable String providedAuthorizationToken) {
        this.providedAuthorizationToken = providedAuthorizationToken;
        return this;
    }

    public WebimClientBuilder setSessionId(@Nullable String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public WebimClientBuilder setAuthData(@Nullable AuthData authData) {
        this.authData = authData;
        return this;
    }

    public WebimClientBuilder setCallbackExecutor(@Nullable Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        return this;
    }

    public WebimClientBuilder setDeltaCallback(@NonNull DeltaCallback deltaCallback) {
        this.deltaCallback = deltaCallback;
        return this;
    }

    public WebimClientBuilder setSessionParamsListener(SessionParamsListener sessionParamsListener) {
        this.sessionParamsListener = sessionParamsListener;
        return this;
    }

    public WebimClientBuilder setErrorListener(@NonNull InternalErrorListener errorListener) {
        this.errorListener = errorListener;
        return this;
    }

    public WebimClientBuilder setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public WebimClientBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public WebimClientBuilder setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public WebimClientBuilder setPrechatFields(String prechatFields) {
        this.prechatFields = prechatFields;
        return this;
    }

    public WebimClientBuilder setRequestHeader(Map<String, String> requestHeader) {
        this.requestHeader = requestHeader;
        return this;
    }

    private static WebimService setupWebimService(
        String url,
        boolean isDelta,
        SSLSocketFactory sslSocketFactory,
        X509TrustManager trustManager,
        ClientInterceptor clientInterceptor,
        HostSelectionInterceptor hostSelectionInterceptor
    ) {
        return new Retrofit.Builder()
                .baseUrl(url)
                .client(setupHttpClient(isDelta, sslSocketFactory, trustManager, clientInterceptor, hostSelectionInterceptor))
                .addConverterFactory(GsonConverterFactory.create(setupGson()))
                .build()
                .create(WebimService.class);
    }

    private static OkHttpClient setupHttpClient(
        final boolean isDelta,
        final SSLSocketFactory sslSocketFactory,
        final X509TrustManager trustManager,
        final ClientInterceptor clientInterceptor,
        final HostSelectionInterceptor hostSelectionInterceptor
    ) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(clientInterceptor);
        builder.addInterceptor(hostSelectionInterceptor);
        builder.readTimeout(isDelta
                        ? DELTA_TIMEOUT_READ_IN_SECONDS
                        : ACTION_TIMEOUT_READ_IN_SECONDS,
                TimeUnit.SECONDS);
        builder.writeTimeout(TIMEOUT_WRITE_IN_SECONDS, TimeUnit.SECONDS);
        builder.callTimeout(TIMEOUT_CALL_IN_SECONDS, TimeUnit.SECONDS);
        if (sslSocketFactory != null && trustManager != null) {
            builder.sslSocketFactory(sslSocketFactory, trustManager);
        }
        if (WebimInternalLog.getInstance() != null) {
            builder.addInterceptor(new WebimHttpLoggingInterceptor(message -> {
                if (isDelta) {
                    WebimInternalLog.getInstance().setLastDeltaResponseJSON(message);
                } else {
                    WebimInternalLog.getInstance().setLastActionResponseJSON(message);
                }
            }));
        }

        return builder.build();
    }

    public WebimClientBuilder setSslSocketFactoryAndTrustManager(SSLSocketFactory sslSocketFactory,
                                                                 X509TrustManager trustManager) {
        this.sslSocketFactory = sslSocketFactory;
        this.trustManager = trustManager;
        return this;
    }

    public WebimClientBuilder setSessionCallback(WebimSession.SessionCallback sessionCallback) {
        this.sessionCallback = sessionCallback;
        return this;
    }

    public WebimClientBuilder setServerConfigsCallback(ServerConfigsCallback serverConfigsCallback) {
        this.serverConfigsCallback = serverConfigsCallback;
        return this;
    }

    public WebimClient build() {
        if ((baseUrl == null)
                || (location == null)
                || (deltaCallback == null)
                || (platform == null)
                || (title == null)
                || (errorListener == null)) {
            throw new IllegalStateException("baseUrl, location, deltaCallback, platform, title " +
                    "and errorListener must be set to non-null value.");
        }
        if (callbackExecutor == null) {
            throw new IllegalStateException("callbackExecutor must be set to non-null value.");
        }

        HostSelectionInterceptor hostSelectionInterceptor = new HostSelectionInterceptor(extraDomains);
        ClientInterceptor clientInterceptor = new ClientInterceptor(requestHeader);
        WebimService deltaService = setupWebimService(baseUrl, true, sslSocketFactory, trustManager, clientInterceptor, hostSelectionInterceptor);
        WebimService actionsService = setupWebimService(baseUrl, false, sslSocketFactory, trustManager, clientInterceptor, hostSelectionInterceptor);

        ActionRequestLoop requestLoop = new ActionRequestLoop(callbackExecutor, errorListener);
        requestLoop.setAuthData(authData);

        ActionRequestLoop pollerLoop = new ActionRequestLoop(callbackExecutor, new IgnoreFatalErrorListener(errorListener));
        pollerLoop.setAuthData(authData);

        DeltaRequestLoop delta = new DeltaRequestLoop(
            deltaCallback,
            new SessionParamsListenerWrapper(sessionParamsListener, requestLoop, pollerLoop),
            callbackExecutor,
            errorListener,
            deltaService,
            platform,
            title,
            location,
            appVersion,
            visitorFieldsJson,
            providedAuthorizationTokenStateListener,
            providedAuthorizationToken,
            deviceId,
            prechatFields,
            pushSystem,
            pushToken,
            visitorJson,
            sessionId,
            authData,
            sessionCallback,
            serverConfigsCallback
        );

        return new WebimClientImpl(
            requestLoop,
            delta,
            pollerLoop,
            new WebimActionsImpl(actionsService, requestLoop, pollerLoop),
            clientInterceptor,
            hostSelectionInterceptor
        );
    }

    private static Gson setupGson() {
        return new GsonBuilder()
            .registerTypeAdapter(DeltaItem.class, new DeltaDeserializer())
            .registerTypeAdapter(DeltaFullUpdate.class, new DeltaFullUpdateDeserializer())
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    }

    private static class IgnoreFatalErrorListener implements InternalErrorListener {
        private final InternalErrorListener errorListener;

        public IgnoreFatalErrorListener(InternalErrorListener errorListener) {
            this.errorListener = errorListener;
        }

        @Override
        public void onError(@NonNull String url, @Nullable String error, int httpCode){
            WebimInternalLog.getInstance().log(
                "Fatal error ignored: url=" + url + " error=" + error + " httpCode=" + httpCode,
                Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR
            );
        }

        @Override
        public void onNotFatalError (@NonNull NotFatalErrorHandler.NotFatalErrorType error){
            errorListener.onNotFatalError(error);
        }
    }

    /**
     * Need to update pushToken in DeltaRequestLoop on update in WebimActions
     */
    private static class WebimClientImpl implements WebimClient {
        @NonNull
        private final WebimActionsImpl actions;
        @NonNull
        private final DeltaRequestLoop deltaLoop;
        @NonNull
        private final ActionRequestLoop requestLoop;
        @NonNull
        private final ActionRequestLoop pollerLoop;
        @NonNull
        private final ClientInterceptor clientInterceptor;
        @NonNull
        private final HostSelectionInterceptor hostSelectionInterceptor;

        private WebimClientImpl(
            @NonNull ActionRequestLoop requestLoop,
            @NonNull DeltaRequestLoop deltaLoop,
            @NonNull ActionRequestLoop pollerLoop,
            @NonNull WebimActionsImpl actions,
            @NonNull ClientInterceptor clientInterceptor,
            @NonNull HostSelectionInterceptor hostSelectionInterceptor
        ) {
            this.requestLoop = requestLoop;
            this.deltaLoop = deltaLoop;
            this.pollerLoop = pollerLoop;
            this.actions = actions;
            this.clientInterceptor = clientInterceptor;
            this.hostSelectionInterceptor = hostSelectionInterceptor;
        }

        @Override
        public void start() {
            requestLoop.start();
            deltaLoop.start();
            pollerLoop.start();
        }

        @Override
        public void pause() {
            requestLoop.pause();
            deltaLoop.pause();
            pollerLoop.pause();
        }

        @Override
        public void resume() {
            requestLoop.resume();
            deltaLoop.resume();
            pollerLoop.resume();

        }

        @Override
        public void stop() {
            deltaLoop.stop();
            requestLoop.stop();
            pollerLoop.stop();
        }

        @NonNull
        @Override
        public WebimActions getActions() {
            return actions;
        }

        @Nullable
        @Override
        public AuthData getAuthData() {
            return deltaLoop.getAuthData();
        }

        @NonNull
        @Override
        public DeltaRequestLoop getDeltaRequestLoop() {
            return deltaLoop;
        }

        @Override
        public void setPushToken(@NonNull String token, @Nullable WebimSession.TokenCallback callback) {
            deltaLoop.setPushToken(token);
            actions.updatePushToken(token, callback);
        }

        @Override
        public void setRequestHeaders(@NonNull Map<String, String> headers) {
            clientInterceptor.setRequestHeader(headers);
        }

        @Override
        public void switchHost() {
            hostSelectionInterceptor.switchDomain();
        }
    }

    private static class ClientInterceptor implements Interceptor {
        private volatile Map<String, String> requestHeader;

        private ClientInterceptor(Map<String, String> requestHeader) {
            this.requestHeader = requestHeader;
        }

        public void setRequestHeader(Map<String, String> requestHeader) {
            this.requestHeader = requestHeader;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request.Builder requestBuilder = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT_STRING);
            if (requestHeader != null) {
                for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }
            return chain.proceed(requestBuilder.build());
        }
    }

    private static class HostSelectionInterceptor implements Interceptor {
        private static final int DEFAULT_DOMAIN_RETRY = -1;
        private final @NonNull List<String> extraDomains;
        private final AtomicInteger retry = new AtomicInteger(DEFAULT_DOMAIN_RETRY);

        public HostSelectionInterceptor(@NonNull List<String> extraDomains) {
            this.extraDomains = extraDomains;
        }

        public void switchDomain() {
            retry.incrementAndGet();
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();

            int localRetry = retry.get();
            if (localRetry == DEFAULT_DOMAIN_RETRY || localRetry >= extraDomains.size()) {
                return chain.proceed(request);
            }

            String newDomain = extraDomains.get(localRetry);
            HttpUrl newUrl = request.url().newBuilder()
                .host(newDomain)
                .build();

            request = request.newBuilder().url(newUrl).build();
            return chain.proceed(request);

        }
    }

    /**
     * Need to update AuthData in ActionRequestLoop on update in DeltaRequestLoop
     */
    private static class SessionParamsListenerWrapper implements SessionParamsListener {
        @Nullable
        private final SessionParamsListener wrapped;
        @NonNull
        private final ActionRequestLoop requestLoop;
        @NonNull
        private final ActionRequestLoop pollerLoop;

        private SessionParamsListenerWrapper(@Nullable SessionParamsListener wrapped,
                                             @NonNull ActionRequestLoop requestLoop,
                                             @NonNull ActionRequestLoop pollerLoop) {
            this.wrapped = wrapped;
            this.requestLoop = requestLoop;
            this.pollerLoop = pollerLoop;
        }

        @Override
        public void onSessionParamsChanged(@NonNull String visitorJson,
                                           @NonNull String sessionId,
                                           @NonNull AuthData authData) {
            requestLoop.setAuthData(authData);
            pollerLoop.setAuthData(authData);
            if (wrapped != null) {
                wrapped.onSessionParamsChanged(visitorJson, sessionId, authData);
            }
        }
    }
}
