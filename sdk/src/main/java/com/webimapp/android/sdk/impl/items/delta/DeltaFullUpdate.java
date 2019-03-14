package com.webimapp.android.sdk.impl.items.delta;

import com.google.gson.annotations.SerializedName;
import com.webimapp.android.sdk.impl.items.ChatItem;
import com.webimapp.android.sdk.impl.items.DepartmentItem;
import java.util.List;

public class DeltaFullUpdate {
    @SerializedName("authToken")
    private String authToken;
    @SerializedName("chat")
    private ChatItem chat;
    @SerializedName("departments")
    private List<DepartmentItem> departments;
    @SerializedName("hintsEnabled")
    private Boolean hintsEnabled;
    @SerializedName("historyRevision")
    private String historyRevision;
    @SerializedName("onlineStatus")
    private String onlineStatus;
    @SerializedName("pageId")
    private String pageId;
    @SerializedName("state")
    private String state; // WMInvitationState
    private String visitorJson; // Manual deserialization "visitor"
    @SerializedName("visitSessionId")
    private String visitSessionId;

    public DeltaFullUpdate() {
        // Fix for gson;
    }

    public String getAuthToken() {
        return authToken;
    }

    public ChatItem getChat() {
        return chat;
    }

    public List<DepartmentItem> getDepartments() {
        return departments;
    }

    public Boolean getHintsEnabled() {
        return hintsEnabled;
    }

    public String getHistoryRevision() {
        return historyRevision;
    }

    public String getOnlineStatus() {
        return onlineStatus;
    }

    public String getPageId() {
        return pageId;
    }

    public String getState() {
        return state;
    }

    public String getVisitorJson() {
        return visitorJson;
    }

    public void setVisitorJson(String visitorJson) {
        this.visitorJson = visitorJson;
    }

    public String getVisitSessionId() {
        return visitSessionId;
    }

    public boolean hasChat() {
        return chat != null;
    }


}
