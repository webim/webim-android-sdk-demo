package com.webimapp.android.sdk.impl;

public interface RemoteHistoryProvider {
    void requestHistoryBefore(long beforeMessageTs, HistoryBeforeCallback callback);
}
