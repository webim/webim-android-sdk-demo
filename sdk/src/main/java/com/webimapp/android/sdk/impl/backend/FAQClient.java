package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;

public interface FAQClient {
    void start();

    void pause();

    void resume();

    void stop();

    @NonNull
    FAQActions getActions();
}
