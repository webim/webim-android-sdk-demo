package ru.webim.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

public class MetaResponse extends ErrorResponse {

    @SerializedName("endpoints")
    private MetaEndpointsResponse endpoints;

    public MetaEndpointsResponse getEndpoints() {
        return endpoints;
    }
}
