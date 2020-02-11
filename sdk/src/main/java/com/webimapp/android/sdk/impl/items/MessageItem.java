package com.webimapp.android.sdk.impl.items;

import android.support.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public final class MessageItem implements Comparable<MessageItem> {
    @SerializedName("authorId")
    private String authorId;
    @SerializedName("avatar")
    private String avatar;
    @SerializedName("canBeReplied")
    private boolean canBeReplied;
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
    @SerializedName("sessionId")
    private String sessionId;
    @SerializedName("text")
    private String text;
    @SerializedName("ts")
    private double tsSeconds;
    @SerializedName("ts_m")
    private long tsMicros = -1;
    @SerializedName("quote")
    private Quote quote;

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

    public void setType(WMMessageKind newKind) {
        kind = newKind;
    }

    public String getMessage() {
        return text;
    }

    public void setMessage(String string) {
        text = string;
    }

    public String getSessionId() {
        return sessionId;
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

    public void setId(String newId) {
        id = newId;
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

    public boolean canBeReplied() {
        return canBeReplied;
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

    public Quote getQuote() {
        return quote;
    }

    public static final class Quote {
        private String id;
        private  WMMessageKind kind;
        @SerializedName("message")
        private QuotedMessage message;
        private String name;
        @SerializedName("state")
        private State state;
        private String text;

        public String getAuthorId() {
            return message.authorId;
        }

        public String getId() {
            return id = message.getId();
        }

        public String getName() {
            return name = message.getName();
        }

        public State getState() {
            return state;
        }

        public WMMessageKind getType() {
            return kind = message.getType();
        }

        public String getText() {
            return text = message.getText();
        }

        public long getTimeSeconds() {
            return message.getTimeSeconds();
        }

        public enum State {
            @SerializedName("pending")
            PENDING,
            @SerializedName("filled")
            FILLED,
            @SerializedName("not-found")
            NOT_FOUND
        }

        public static final class QuotedMessage {
            @SerializedName("authorId")
            private String authorId;
            @SerializedName("id")
            private String id;
            @SerializedName("kind")
            private  WMMessageKind kind;
            @SerializedName("name")
            private String name;
            @SerializedName("text")
            private String text;
            @SerializedName("ts")
            private long tsSeconds;

            public String getAuthorId() {
                return authorId;
            }

            public String getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public WMMessageKind getType() {
                return kind;
            }

            public String getText() {
                return text;
            }

            public long getTimeSeconds() {
                return tsSeconds;
            }
        }

        public static String getRawQuote(
                String state,
                String senderName,
                String text,
                String type,
                long tsSeconds,
                String authorId,
                String id) {
            return "{\"state\":\"" + state.toLowerCase().replace("_","-") + "\"," +
                    "\"message\":{" +
                        "\"channelSideId\":null," +
                        "\"name\":" + checkOnNull(senderName) + "," +
                        "\"text\":" + checkOnAttachment(text, type) + "," +
                        "\"kind\":" + convertingKind(type) + "," +
                        "\"ts\":" + tsSeconds + "," +
                        "\"authorId\":" + checkOnNull(authorId) +  "," +
                        "\"id\":" + checkOnNull(id) +
                        "}" +
                    "}";
        }
        
        private static String checkOnNull(String checkValue) {
            return (checkValue != null) ? "\"" + checkValue + "\"" : null;
        }

        private static String checkOnAttachment(String checkValue, String messageType) {
            if (messageType != null) {
                return (messageType.equals(WMMessageKind.FILE_FROM_OPERATOR.toString())
                        || messageType.equals(WMMessageKind.FILE_FROM_VISITOR.toString()))
                        ? checkOnNull(percentEncode(checkValue))
                        : checkOnNull(checkValue);
            } else {
                return null;
            }
        }

        private static String convertingKind(String type) {
            return (type != null)
                    ? checkOnNull(WMMessageKindForSaveInDb.valueOf(type).toString())
                    : null;
        }

        private static String percentEncode(String input) {
            if ((input == null) || input.isEmpty()) {
                return input;
            }

            String CHARACTERS_TO_ENCODE = "\n\"\\%";
            StringBuilder result = new StringBuilder(input);
            for (int i = (input.length() - 1); i >= 0; i--) {
                if (CHARACTERS_TO_ENCODE.indexOf(input.charAt(i)) != -1) {
                    result.replace(
                            i,
                            (i + 1),
                            ("%" + Integer.toHexString(0x100 | input.charAt(i))
                                    .substring(1).toUpperCase())
                    );
                }
            }

            return result.toString();
        }

        public enum WMMessageKindForSaveInDb {
            ACTION_REQUEST ("action_request"),
            CONTACT_REQUEST ("cont_req"),
            CONTACTS ("contacts"),
            FILE_FROM_OPERATOR ("file_operator"),
            FOR_OPERATOR ("for_operator"),
            FILE_FROM_VISITOR ("file_visitor"),
            INFO ("info"),
            KEYBOARD ("keyboard"),
            KEYBOARD_RESPONCE ("keyboard_response"),
            OPERATOR ("operator"),
            OPERATOR_BUSY ("operator_busy"),
            VISITOR ("visitor");

            private final String kind;

            WMMessageKindForSaveInDb(String jsonKind) {
                kind = jsonKind;
            }

            @Override
            public String toString() {
                return kind;
            }
        }

    }
}
