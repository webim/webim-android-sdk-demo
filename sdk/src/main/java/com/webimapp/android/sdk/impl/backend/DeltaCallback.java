package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;

import java.util.List;

import com.webimapp.android.sdk.impl.items.delta.DeltaFullUpdate;
import com.webimapp.android.sdk.impl.items.delta.DeltaItem;

public interface DeltaCallback {
    void onFullUpdate(@NonNull DeltaFullUpdate fullUpdate);

    void onDeltaList(@NonNull List<DeltaItem> list);
}
