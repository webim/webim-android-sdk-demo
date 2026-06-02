package ru.webim.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

public class AuthResponse extends ErrorResponse {
    @SerializedName("authToken")
    private String authToken;
    private String visitorJson;

    public String getAuthToken() {
        return authToken;
    }

    public void setVisitor(String visitor) {
        visitorJson = visitor;
    }

    public String getVisitor() {
        return visitorJson;
    }
}
