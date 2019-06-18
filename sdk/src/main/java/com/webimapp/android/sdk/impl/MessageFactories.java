package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.impl.backend.WebimClient;
import com.webimapp.android.sdk.impl.items.KeyboardItem;
import com.webimapp.android.sdk.impl.items.KeyboardRequestItem;
import com.webimapp.android.sdk.impl.items.MessageItem;
import com.webimapp.android.sdk.impl.items.OperatorItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class MessageFactories {

    // may return null for incorrect or not supported message
    @Nullable
    private static MessageImpl fromWMMessage(@NonNull String serverUrl,
                                             boolean isHistoryMessage,
                                             @NonNull MessageItem message,
                                             @NonNull WebimClient client) {
        MessageItem.WMMessageKind kind = message.getType();

        if ((((kind == null)
                || (kind == MessageItem.WMMessageKind.CONTACTS))
                || (kind == MessageItem.WMMessageKind.FOR_OPERATOR))) {
            return null;
        }

        Message.Attachment attachment = null;
        String text;
        String rawText;

        if ((kind == MessageItem.WMMessageKind.FILE_FROM_VISITOR)
                || (kind == MessageItem.WMMessageKind.FILE_FROM_OPERATOR)) {
            attachment = InternalUtils.getAttachment(serverUrl, message, client);
            if (attachment == null) {
                return null;
            }
            text = attachment.getFileName();
            rawText = message.getMessage();
        } else {
            text = (message.getMessage() == null) ? "" : message.getMessage();
            rawText = null;
        }

        Object json = message.getData();
        String data = json == null
                ? null
                : new Gson().toJson(json);

        Message.Keyboard keyboardButton = null;
        if (kind == MessageItem.WMMessageKind.KEYBOARD) {
            Type mapType = new TypeToken<KeyboardItem>(){}.getType();
            KeyboardItem keyboard = InternalUtils.getKeyboard(data, isHistoryMessage, mapType);
            keyboardButton = InternalUtils.getKeyboardButton(keyboard);
        }

        Message.KeyboardRequest keyboardRequest = null;
        if (kind == MessageItem.WMMessageKind.KEYBOARD_RESPONCE) {
            Type mapType = new TypeToken<KeyboardRequestItem>(){}.getType();
            KeyboardRequestItem keyboard = InternalUtils.getKeyboard(data, isHistoryMessage, mapType);
            keyboardRequest = InternalUtils.getKeyboardRequest(keyboard);
        }

        return new MessageImpl(serverUrl,
                StringId.forMessage(message.getClientSideId()),
                InternalUtils.getOperatorId(message),
                message.getSenderAvatarUrl(),
                message.getSenderName(),
                InternalUtils.toPublicMessageType(kind),
                text,
                message.getTimeMicros(),
                attachment,
                message.getId(),
                rawText,
                isHistoryMessage,
                data,
                message.isRead(),
                message.canBeEdited(),
                keyboardButton,
                keyboardRequest);
    }

    public interface Mapper<T extends MessageImpl> {
        @Nullable
        T map(MessageItem msg);

        @NonNull
        List<T> mapAll(@NonNull List<MessageItem> list);

        void setClient(@NonNull WebimClient client);
    }

    public static abstract class AbstractMapper<T extends MessageImpl> implements Mapper<T> {
        @NonNull
        protected final String serverUrl;
        protected WebimClient client;

        protected AbstractMapper(@NonNull String serverUrl, @Nullable WebimClient client) {
            this.serverUrl = serverUrl;
            this.client = client;
        }

        @NonNull
        @Override
        public List<T> mapAll(@NonNull List<MessageItem> list) {
            List<T> ret = new ArrayList<>(list.size());
            for (MessageItem item : list) {
                T msg = map(item);
                if (msg != null) {
                    ret.add(msg);
                }
            }
            return ret;
        }
    }

    public static class MapperHistory extends AbstractMapper<MessageImpl> {

        protected MapperHistory(@NonNull String serverUrl, @Nullable WebimClient client) {
            super(serverUrl, client);
        }

        @Nullable
        @Override
        public MessageImpl map(MessageItem msg) {
            return fromWMMessage(serverUrl, true, msg, client);
        }

        @Override
        public void setClient(@NonNull WebimClient client) {
            this.client = client;
        }
    }

    public static class MapperCurrentChat extends AbstractMapper<MessageImpl> {

        protected MapperCurrentChat(@NonNull String serverUrl, @Nullable WebimClient client) {
            super(serverUrl, client);
        }

        @Nullable
        @Override
        public MessageImpl map(MessageItem msg) {
            return fromWMMessage(serverUrl, false, msg, client);
        }

        @Override
        public void setClient(@NonNull WebimClient client) {
            this.client = client;
        }
    }

    public static class SendingFactory {
        private final String serverUrl;

        public SendingFactory(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public MessageSending createText(Message.Id id, String text) {
            return new MessageSending(serverUrl, id, "", Message.Type.VISITOR,
                    text, System.currentTimeMillis() * 1000);
        }

        public MessageSending createFile(Message.Id id) {
            return new MessageSending(serverUrl, id, "", Message.Type.FILE_FROM_VISITOR,
                    "", System.currentTimeMillis() * 1000);
        }
    }

    public static class OperatorFactory {
        private final String serverUrl;

        public OperatorFactory(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        @Nullable
        public Operator createOperator(@Nullable OperatorItem operatorItem) {
            return operatorItem == null
                    ? null
                    : new OperatorImpl(StringId.forOperator(operatorItem.getId()),
                    operatorItem.getFullname(),
                    operatorItem.getAvatar() == null ? null : serverUrl + operatorItem.getAvatar());
        }
    }
}
