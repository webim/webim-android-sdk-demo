package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;

public interface SessionParamsListener {
    void onSessionParamsChanged(@NonNull String visitorJson,
                                @NonNull String sessionId,
                                @NonNull AuthData pageId);
}
