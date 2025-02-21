package ru.webim.android.sdk.impl.backend;

import androidx.annotation.Nullable;

import ru.webim.android.sdk.impl.items.AccountConfigItem;
import ru.webim.android.sdk.impl.items.LocationSettingsItem;

public interface ServerConfigsCallback {
    void onServerConfigs(
        @Nullable AccountConfigItem accountConfigItem,
        @Nullable LocationSettingsItem locationSettingsItem
    );
}