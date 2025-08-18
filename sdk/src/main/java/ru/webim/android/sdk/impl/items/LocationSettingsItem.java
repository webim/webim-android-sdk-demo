package ru.webim.android.sdk.impl.items;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class LocationSettingsItem {
    @SerializedName("chat")
    private Chat chat;
    @SerializedName("informingSubscribers")
    @Nullable
    private InformingSubscribers informingSubscribers;

    public Chat getChat() {
        return chat;
    }

    @Nullable
    public InformingSubscribers getInformingSubscribers() {
        return informingSubscribers;
    }

    public static class Chat {
        @SerializedName("proposeToRateBeforeClose")
        private ToggleValue proposeToRateBeforeClose;
        @SerializedName("lang")
        private String language;

        public ToggleValue getProposeToRateBeforeClose() {
            return proposeToRateBeforeClose;
        }

        public String getLanguage() {
            return language;
        }
    }

    public enum ToggleValue {
        @SerializedName("Y")
        ENABLED,
        @SerializedName("N")
        DISABLED
    }

    public static class InformingSubscribers {
        @SerializedName("enabled")
        private ToggleValue enabled;
        @SerializedName("writeToOperator")
        private ToggleValue writeToOperator;
        @SerializedName("text")
        private String text;

        public ToggleValue isEnabled() {
            return enabled;
        }

        public ToggleValue getWriteToOperator() {
            return writeToOperator;
        }

        public String getText() {
            return text;
        }
    }
}
