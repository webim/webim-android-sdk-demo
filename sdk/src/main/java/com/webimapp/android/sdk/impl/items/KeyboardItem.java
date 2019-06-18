package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class KeyboardItem {
    @SerializedName("buttons")
    private List<List<Buttons>> buttons;
    @SerializedName("state")
    private String state;
    @SerializedName("response")
    private Response response;

    public String getState() {
        return state;
    }

    public List<List<Buttons>> getButtons() {
        return buttons;
    }

    public Response getResponse() {
        return response;
    }

    public final class Buttons {
        @SerializedName("id")
        private String id;
        @SerializedName("text")
        private String text;

        public String getId(){
            return id;
        }

        public String getText() {
            return text;
        }
    }

    public final class Response {
        @SerializedName("buttonId")
        private String buttonId;
        @SerializedName("messageId")
        private String messageId;

        public String getButtonId() {
            return buttonId;
        }

        public String getMessageId() {
            return messageId;
        }
    }
}
