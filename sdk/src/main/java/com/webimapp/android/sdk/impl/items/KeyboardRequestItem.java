package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class KeyboardRequestItem {
    @SerializedName("button")
    private Button button;
    @SerializedName("request")
    private Request request;

    public Button getButton() {
        return button;
    }

    public Request getRequest() {
        return  request;
    }

    public final class Button {
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

    public final class Request {
        @SerializedName("messageId")
        private String messageId;

        public String getMessageId() {
            return messageId;
        }
    }

}
