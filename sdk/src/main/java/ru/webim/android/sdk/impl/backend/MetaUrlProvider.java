package ru.webim.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ru.webim.android.sdk.impl.items.responses.MetaEndpointsResponse;
import ru.webim.android.sdk.impl.items.responses.MetaResponse;

public class MetaUrlProvider {
    private MetaResponse metaResponse;

    public void setMetaResponse(@Nullable MetaResponse metaResponse) {
        this.metaResponse = metaResponse;
    }

    @NonNull
    public String getHistoryUrl() {
        MetaEndpointsResponse endpoints = getEndpoints();
        if (endpoints != null && endpoints.getHistory() != null && endpoints.getHistory().getUrl() != null) {
            return endpoints.getHistory().getUrl();
        } else {
            return WebimService.URL_SUFFIX_HISTORY;
        }
    }

    @NonNull
    public String getConfigsUrl() {
        MetaEndpointsResponse endpoints = getEndpoints();
        if (endpoints != null && endpoints.getConfigs() != null && endpoints.getConfigs().getUrl() != null) {
            return endpoints.getConfigs().getUrl();
        } else {
            return WebimService.URL_SUFFIX_GET_CONFIG;
        }
    }

    @NonNull
    public String getAuthUrl() {
        MetaEndpointsResponse endpoints = getEndpoints();
        if (endpoints != null && endpoints.getAuth() != null && endpoints.getAuth().getUrl() != null) {
            return endpoints.getAuth().getUrl();
        } else {
            return WebimService.URL_SUFFIX_AUTH;
        }
    }

    @NonNull
    public String getInfoUrl() {
        MetaEndpointsResponse endpoints = getEndpoints();
        if (endpoints != null && endpoints.getInfo() != null && endpoints.getInfo().getUrl() != null) {
            return endpoints.getConfigs().getUrl();
        } else {
            return WebimService.URL_SUFFIX_GET_INFO;
        }
    }

    private MetaEndpointsResponse getEndpoints() {
        if (metaResponse != null) {
            return metaResponse.getEndpoints();
        } else {
            return null;
        }
    }
}
