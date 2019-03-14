package com.webimapp.android.sdk.impl;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.FAQ;
import com.webimapp.android.sdk.impl.backend.DefaultCallback;
import com.webimapp.android.sdk.impl.backend.FAQClient;
import com.webimapp.android.sdk.impl.backend.FAQClientBuilder;
import com.webimapp.android.sdk.impl.backend.InternalErrorListener;
import com.webimapp.android.sdk.impl.items.FAQCategoryItem;
import com.webimapp.android.sdk.impl.items.FAQItemItem;
import com.webimapp.android.sdk.impl.items.FAQStructureItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class FAQImpl implements FAQ {
    @NonNull
    private final AccessChecker accessChecker;
    @NonNull
    private final FAQClient client;
    @NonNull
    private final FAQDestroyer destroyer;
    private boolean clientStarted;

    private FAQImpl(
            @NonNull AccessChecker accessChecker,
            @NonNull FAQDestroyer destroyer,
            @NonNull FAQClient client
    ) {
        this.accessChecker = accessChecker;
        this.destroyer = destroyer;
        this.client = client;
    }


    public static FAQ newInstance(String accountName,
                                  SSLSocketFactory sslSocketFactory,
                                  X509TrustManager trustManager) {
        accountName.getClass(); // NPE

        if (Looper.myLooper() == null) {
            throw new RuntimeException("The Thread on which Webim session creates " +
                    "should have attached android.os.Looper object.");
        }

        String serverUrl = InternalUtils.createServerUrl(accountName);

        Handler handler = new Handler();

        FAQDestroyer destroyer = new FAQDestroyer();
        AccessCheckerImpl accessChecker =
                new AccessCheckerImpl(Thread.currentThread(), destroyer);


        final FAQClient client = new FAQClientBuilder()
                .setBaseUrl(serverUrl)
                .setCallbackExecutor(new ExecIfNotDestroyedHandlerExecutor(destroyer, handler))
                .setSslSocketFactoryAndTrustManager(sslSocketFactory, trustManager)
                .build();

        destroyer.addDestroyAction(new Runnable() {
            @Override
            public void run() {
                client.stop();
            }
        });


        return new FAQImpl(accessChecker, destroyer, client);
    }

    private void checkAccess() {
        accessChecker.checkAccess();
    }

    @Override
    public void resume() {
        checkAccess();
        if (!clientStarted) {
            client.start();
            clientStarted = true;
        }
        client.resume();
    }

    @Override
    public void pause() {
        if (destroyer.isDestroyed()) {
            return;
        }
        checkAccess();
        client.pause();
    }

    @Override
    public void destroy() {
        if (destroyer.isDestroyed()) {
            return;
        }
        checkAccess();
    }

    @Override
    public void getStructure(int id, final GetStructureCallback callback) {
        accessChecker.checkAccess();

        client.getActions().getStructure(id, new DefaultCallback<FAQStructureItem>() {
            @Override
            public void onSuccess(FAQStructureItem response) {
                callback.receive(response);
            }
        });
    }

    @Override
    public void getCategory(int id, final GetCategoryCallback callback) {
        accessChecker.checkAccess();

        client.getActions().getCategory(id, new DefaultCallback<FAQCategoryItem>() {
            @Override
            public void onSuccess(FAQCategoryItem response) {
                callback.receive(response);
            }
        });
    }

    @Override
    public void getItem(String id, final GetItemCallback callback) {
        accessChecker.checkAccess();

        client.getActions().getItem(id, new DefaultCallback<FAQItemItem>() {
            @Override
            public void onSuccess(FAQItemItem response) {
                callback.receive(response);
            }
        });
    }

    private static class FAQDestroyer {
        @NonNull
        private final List<Runnable> actions = new ArrayList<>();
        private boolean destroyed;

        public FAQDestroyer() {
        }

        public void addDestroyAction(Runnable action) {
            actions.add(action);
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        public void destroy() {
            if (!destroyed) {
                destroyed = true;
                for (Runnable action : actions) {
                    action.run();
                }
            }
        }
    }

    private static class AccessCheckerImpl implements AccessChecker {

        @NonNull
        private final Thread thread;
        @NonNull
        private final FAQDestroyer destroyer;

        public AccessCheckerImpl(@NonNull Thread thread, @NonNull FAQDestroyer destroyer) {
            this.thread = thread;
            this.destroyer = destroyer;
        }

        @Override
        public void checkAccess() {
            if (thread != Thread.currentThread()) {
                throw new RuntimeException("All Webim actions must be invoked from"
                        + " thread on which the session has been created. " +
                        "Created on: " + thread + ", current thread: " + Thread.currentThread());
            }
            if (destroyer.isDestroyed()) {
                throw new IllegalStateException("Can't use destroyed session");
            }
        }
    }

    private static class ExecIfNotDestroyedHandlerExecutor implements Executor {
        private final FAQDestroyer destroyed;
        private final Handler handled;

        private ExecIfNotDestroyedHandlerExecutor(FAQDestroyer destroyed, Handler handled) {
            this.destroyed = destroyed;
            this.handled = handled;
        }

        @Override
        public void execute(final @NonNull Runnable command) {
            if (!destroyed.isDestroyed()) {
                handled.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed.isDestroyed()) {
                            command.run();
                        }
                    }
                });
            }
        }
    }

    private static class DestroyIfNotErrorListener implements InternalErrorListener {
        @Nullable
        private final FAQDestroyer destroyer;
        @Nullable
        private final InternalErrorListener errorListener;

        private DestroyIfNotErrorListener(@Nullable FAQDestroyer destroyer,
                                          @Nullable InternalErrorListener errorListener) {
            this.destroyer = destroyer;
            this.errorListener = errorListener;
        }

        @Override
        public void onError(@NonNull String url, @Nullable String error, int httpCode) {
            if (destroyer == null || !destroyer.isDestroyed()) {
                if (destroyer != null) {
                    destroyer.destroy();
                }
                if (errorListener != null) {
                    errorListener.onError(url, error, httpCode);
                }
            }
        }
    }
}
