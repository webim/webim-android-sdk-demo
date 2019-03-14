package com.webimapp.android.sdk.impl.backend;

public interface SendOrDeleteMessageInternalCallback {
    void onSuccess();

    void onFailure(String error);
}
