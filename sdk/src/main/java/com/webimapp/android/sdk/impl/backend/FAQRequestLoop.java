package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.impl.items.responses.ErrorResponse;

import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import retrofit2.Call;

public class FAQRequestLoop extends AbstractRequestLoop {
    private final BlockingQueue<FAQRequestLoop.WebimRequest> queue = new ArrayBlockingQueue<>(128);
    @NonNull
    private final Executor callbackExecutor;
    @Nullable
    private FAQRequestLoop.WebimRequest<?> lastRequest;

    public FAQRequestLoop(@NonNull Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    protected void run() {
        try {
            while (isRunning()) {
                try {
                    if (!isRunning()) {
                        return;
                    }
                    runIteration();
                } catch (final AbortByWebimErrorException e) {
                    if (WebimInternalError.WRONG_ARGUMENT_VALUE.equals(e.getError())) {
                        WebimInternalLog.getInstance().log("Error: " + "\""+ e.getError() + "\""
                                        + ", argumentName: " + "\"" + e.getArgumentName() + "\"",
                                Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);
                        this.lastRequest = null;
                    } else {
                        running = false;
                    }
                } catch (SocketTimeoutException e) {
                    WebimInternalLog.getInstance().log(e.toString(),
                            Webim.SessionBuilder.WebimLogVerbosityLevel.DEBUG);
                    this.lastRequest = null;
                } catch (InterruptedRuntimeException ignored) { }
            }
        } catch (final Throwable t) {
            running = false;

            throw t;
        }
    }

    @SuppressWarnings("unchecked")
    private void runIteration() throws SocketTimeoutException {
        FAQRequestLoop.WebimRequest<?> currentRequest = this.lastRequest;
        if (currentRequest == null) {
            try {
                this.lastRequest = currentRequest = queue.take();
            } catch (InterruptedException e) {
                return;
            }
        }

        try {
            performRequestAndCallback(currentRequest);
        } catch (final FileNotFoundException ignored) {
        } catch (AbortByWebimErrorException exception) {
            if ((exception.getError() != null)
                    && currentRequest.isHandleError(exception.getError())) {
                if (currentRequest.hasCallback) {
                    final FAQRequestLoop.WebimRequest<?> callback = currentRequest;
                    final String error = exception.getError();
                    callbackExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.handleError(error);
                        }
                    });
                } // Else ignore.
            } else {
                throw exception;
            }
        }

        this.lastRequest = null;
    }

    private <T extends ErrorResponse> void performRequestAndCallback
            (FAQRequestLoop.WebimRequest<T> currentRequest)
            throws SocketTimeoutException, FileNotFoundException {
        final T response = performRequest(currentRequest.makeRequest());

        if (currentRequest.hasCallback) {
            final FAQRequestLoop.WebimRequest<T> callback = currentRequest;
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    callback.runCallback(response);
                }
            });
        }
    }

    void enqueue(FAQRequestLoop.WebimRequest<?> request) {
        try {
            queue.put(request);
        } catch (InterruptedException ignored) { }
    }

    static abstract class WebimRequest<T extends ErrorResponse> {
        private final boolean hasCallback;

        protected WebimRequest(boolean hasCallback) {
            this.hasCallback = hasCallback;
        }

        public abstract Call<T> makeRequest();

        public void runCallback(T response) { }

        public boolean isHandleError(@NonNull String error) {
            return false;
        }

        public void handleError(@NonNull String error) { }
    }
}
