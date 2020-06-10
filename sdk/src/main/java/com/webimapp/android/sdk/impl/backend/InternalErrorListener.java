package com.webimapp.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.webimapp.android.sdk.NotFatalErrorHandler;

public interface InternalErrorListener {
    void onError(@NonNull String url, @Nullable String error, int httpCode);

    void onNotFatalError(@NonNull NotFatalErrorHandler.NotFatalErrorType error);
}
