package com.webimapp.android.sdk.impl;

import android.support.annotation.Nullable;

import java.util.List;
import java.util.Set;

public interface HistorySinceCallback {
    void onSuccess(List<MessageImpl> messages,
                   Set<String> deleted,
                   boolean hasMore,
                   boolean isInitial,
                   @Nullable String revision);
}
