package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class LocationSettingsItem {
    @SerializedName("chat")
    private Chat chat;

    public Chat getChat() {
        return chat;
    }

    public static class Chat {
        @SerializedName("proposeToRateBeforeClose")
        private ToggleValue proposeToRateBeforeClose;

        public ToggleValue getProposeToRateBeforeClose() {
            return proposeToRateBeforeClose;
        }
    }

    public enum ToggleValue {
        @SerializedName("Y")
        ENABLED,
        @SerializedName("N")
        DISABLED
    }
}
