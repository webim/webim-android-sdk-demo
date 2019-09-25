package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.NotFatalErrorHandler;

public interface InternalErrorListener {
    void onError(@NonNull String url, @Nullable String error, int httpCode);

    void onNotFatalError(@NonNull NotFatalErrorHandler.NotFatalErrorType error);
}
