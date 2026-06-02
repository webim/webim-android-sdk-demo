package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class TranslationOptionsItem {

    @SerializedName("enabled")
    private boolean enabled;
    @SerializedName("language")
    private String language;

    @Nullable
    public String getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
