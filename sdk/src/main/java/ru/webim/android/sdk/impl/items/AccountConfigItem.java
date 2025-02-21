package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class AccountConfigItem {
    @SerializedName("visitor_hints_api_endpoint")
    private String hintsEndpoint;
    @SerializedName("rate_operator")
    private boolean rateOperator;
    @SerializedName("disabling_message_input_field")
    private boolean disablingMessageInputField;
    @SerializedName("check_visitor_auth")
    private boolean checkVisitorAuth;
    @SerializedName("web_and_mobile_quoting")
    private boolean quotingEnable = true;

    public String getHintsEndpoint() {
        return hintsEndpoint;
    }

    public boolean isRateOperator() {
        return rateOperator;
    }

    public boolean isDisablingMessageInputField() {
        return disablingMessageInputField;
    }

    public boolean isCheckVisitorAuth() {
        return checkVisitorAuth;
    }

    public boolean isQuotingEnable() {
        return quotingEnable;
    }
}
