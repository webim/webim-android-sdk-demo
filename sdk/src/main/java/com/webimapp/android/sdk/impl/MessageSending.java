package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;

public class MessageSending extends MessageImpl {
    public MessageSending( @NonNull String serverUrl,
            @NonNull Id id,
            @NonNull String senderName,
            @NonNull Type type,
            @NonNull String text,
            long timeMicros) {
        super(serverUrl,
                id,
                null,
                null,
                senderName,
                type,
                text,
                timeMicros,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null);
    }

    @NonNull
    @Override
    public SendStatus getSendStatus() {
        return SendStatus.SENDING;
    }
}
