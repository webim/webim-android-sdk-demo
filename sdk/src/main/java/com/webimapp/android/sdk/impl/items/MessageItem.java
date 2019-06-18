package com.webimapp.android.sdk.impl.items;

import android.support.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public final class MessageItem implements Comparable<MessageItem> {
    @SerializedName("authorId")
    private String authorId;
    @SerializedName("avatar")
    private String avatar;
    @SerializedName("clientSideId")
    private String clientSideId;

    /*
    There's no need to unparse this data.
    The field exist only to pass it from an app to server and vice versa.
    But generally this field contains JSON object.
    */
    @SerializedName("data")
    private Object data;

    @SerializedName("canBeEdited")
    private boolean canBeEdited;
    @SerializedName("chatId")
    private String chatId;
    @SerializedName("deleted")
    private boolean deleted;
    @SerializedName("edited")
    private boolean edited;
    @SerializedName("id")
    private String id;
    @SerializedName("kind")
    private WMMessageKind kind;
    @SerializedName("name")
    private String name;
    @SerializedName("read")
    private boolean read;
    @SerializedName("text")
    private String text;
    @SerializedName("ts")
    private double tsSeconds;
    @SerializedName("ts_m")
    private long tsMicros = -1;

    public MessageItem() {
        // Need for Gson No-args fix
    }

    public MessageItem(String id, String clientSideId, long time, String text) { // debug
        this.id = id;
        this.clientSideId = clientSideId;
        this.tsSeconds = time;
        this.text = text;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getSenderId() {
        return authorId;
    }

    public String getSenderAvatarUrl() {
        return avatar;
    }

    public Object getData() {
        return data;
    }

    public String getSenderName() {
        return name;
    }

    public WMMessageKind getType() {
        return kind;
    }

    public String getMessage() {
        return text;
    }

    public void setMessage(String string) {
        text = string;
    }

    public long getTimeMillis() {
        return tsMicros != -1 ? tsMicros / 1000 : (long) (tsSeconds * 1000L);
    }

    public long getTimeMicros() {
        return tsMicros != -1 ? tsMicros : (long) (tsSeconds * 1000_000L);
    }

    public String getId() {
        return id;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public boolean isTextMessage() {
        switch (getType()) {
            case VISITOR:
            case OPERATOR:
                return true;
            default:
                return false;
        }
    }

    public boolean isFileMessage() {
        switch (getType()) {
            case FILE_FROM_OPERATOR:
            case FILE_FROM_VISITOR:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeEdited() {
        return canBeEdited;
    }

    @Override
    public int compareTo(@NonNull MessageItem another) {
        return (int) (this.getTimeMillis() - another.getTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MessageItem)) {
            return false;
        }
        MessageItem another = (MessageItem) o;
        return clientSideId.equals(another.getClientSideId());
    }

    public String getClientSideId() {
        if (clientSideId == null) {
            clientSideId = id;
        }
        return clientSideId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public String toString() {

        return "MessageItem{" + "\nclientSideId='" + clientSideId + '\'' +
                ", \nid='" + id + '\'' +
                ", \nchatId='" + chatId + '\'' +
                ", \ndeleted='" + deleted + '\'' +
                ", \ntsMicros='" + tsMicros + '\'' +
                "\n}";
    }

    public enum WMMessageKind {
        @SerializedName("action_request")
        ACTION_REQUEST,
        @SerializedName("cont_req")
        CONTACT_REQUEST,
        @SerializedName("contacts")
        CONTACTS,
        @SerializedName("file_operator")
        FILE_FROM_OPERATOR,
        @SerializedName("for_operator")
        FOR_OPERATOR,
        @SerializedName("file_visitor")
        FILE_FROM_VISITOR,
        @SerializedName("info")
        INFO,
        @SerializedName("keyboard")
        KEYBOARD,
        @SerializedName("keyboard_response")
        KEYBOARD_RESPONCE,
        @SerializedName("operator")
        OPERATOR,
        @SerializedName("operator_busy")
        OPERATOR_BUSY,
        @SerializedName("visitor")
        VISITOR
    }
}
