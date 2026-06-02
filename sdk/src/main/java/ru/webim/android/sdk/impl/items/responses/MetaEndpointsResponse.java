package ru.webim.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

public class MetaEndpointsResponse {

    @SerializedName("history_mobile")
    private MetaUrlItem history;
    @SerializedName("configs")
    private MetaUrlItem configs;
    @SerializedName("auth")
    private MetaUrlItem auth;
    @SerializedName("info")
    private MetaUrlItem info;

    public MetaUrlItem getHistory() {
        return history;
    }

    public MetaUrlItem getConfigs() {
        return configs;
    }

    public MetaUrlItem getAuth() {
        return auth;
    }

    public MetaUrlItem getInfo() {
        return info;
    }

    public static class MetaUrlItem {
        @SerializedName("url")
        private String url;

        public String getUrl() {
            return url;
        }
    }
}
