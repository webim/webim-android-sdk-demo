package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class MessageGroup {
    @SerializedName("group")
    private GroupData groupData;
    public GroupData getGroupData() {
        return groupData;
    }

    public static class GroupData {
        @SerializedName("id")
        private String id;
        @SerializedName("msg_count")
        private int msgCount;
        @SerializedName("msg_number")
        private int msgNumber;

        public String getId() {
            return id;
        }

        public int getMsgCount() {
            return msgCount;
        }

        public int getMsgNumber() {
            return msgNumber;
        }
    }
}
