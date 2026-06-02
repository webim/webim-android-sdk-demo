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
    @SerializedName("max_visitor_upload_file_size")
    private int maxVisitorUploadFileSize;
    @SerializedName("allowed_upload_file_types")
    private String allowedUploadFileTypes;

    @SerializedName("rate_form")
    private RateForm rateForm;
    @SerializedName("rated_entity")
    private RatedEntity ratedEntity;
    @SerializedName("visitor_segment")
    private VisitorSegment visitorSegment;
    @SerializedName("messages_translator")
    private boolean messagesTranslator;

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

    public boolean getMessagesTranslator() {
        return messagesTranslator;
    }

    public int getMaxVisitorUploadFileSize() {
        return maxVisitorUploadFileSize;
    }

    public String getAllowedUploadFileTypes() {
        return allowedUploadFileTypes;
    }

    public RateForm getRateForm() {
        return rateForm;
    }

    public RatedEntity getRatedEntity() {
        return ratedEntity;
    }

    public VisitorSegment getVisitorSegment() {
        return visitorSegment;
    }

    public enum RateForm {
        @SerializedName("standard")
        STANDARD,
        @SerializedName("resolution")
        RESOLUTION,
        @SerializedName("combined")
        COMBINED,
    }

    public enum RatedEntity {
        @SerializedName("bot_only")
        BOT_ONLY,
        @SerializedName("operator_only")
        OPERATOR_ONLY,
        @SerializedName("both")
        BOTH,
    }

    public enum VisitorSegment {
        @SerializedName("vip_segment")
        VIP_SEGMENT,
        @SerializedName("mass_segment")
        MASS_SEGMENT,
        @SerializedName("both")
        BOTH,
    }
}
