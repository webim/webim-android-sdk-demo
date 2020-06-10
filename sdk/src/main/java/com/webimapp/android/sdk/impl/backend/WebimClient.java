package com.webimapp.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface WebimClient {

    void start();

    void pause();

    void resume();

    void stop();

    @NonNull WebimActions getActions();

    @Nullable AuthData getAuthData();

    @NonNull DeltaRequestLoop getDeltaRequestLoop();

    void setPushToken(@NonNull String token);

}
