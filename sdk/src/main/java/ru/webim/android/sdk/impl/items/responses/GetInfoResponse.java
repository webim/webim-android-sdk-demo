package ru.webim.android.sdk.impl.items.responses;

import com.google.gson.annotations.SerializedName;

public class GetInfoResponse extends ErrorResponse {
    @SerializedName("unreadMessagesCount")
    private int unreadMessagesCount;
    @SerializedName("historyLastChangeTs")
    private long historyLastChangeTs;

    public int getUnreadMessagesCount() {
        return unreadMessagesCount;
    }

    public long getHistoryLastChangeTs() {
        return historyLastChangeTs;
    }
}
