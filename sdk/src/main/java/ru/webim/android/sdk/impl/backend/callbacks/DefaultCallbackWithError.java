package ru.webim.android.sdk.impl.backend.callbacks;

public interface DefaultCallbackWithError<T> {
    void onSuccess(T response);
    void onFailure(String error);
}
