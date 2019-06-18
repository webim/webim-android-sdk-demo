package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.Operator;

import java.util.List;

public class MessageImpl implements Message, TimeMicrosHolder {

    protected final @Nullable String avatarUrl;
    protected final @NonNull Id id;
    protected final @Nullable Keyboard keyboard;
    protected final @Nullable KeyboardRequest keyboardRequest;
    protected final @Nullable Operator.Id operatorId;
    protected final @NonNull String senderName;
    protected final @NonNull String serverUrl;
    protected final @NonNull String text;
    protected final long timeMicros;
    protected final @NonNull Type type;

    protected @NonNull SendStatus sendStatus = SendStatus.SENT;
    protected @Nullable Attachment attachment;

    private final @Nullable String data;
    private final @Nullable String rawText;

    private String currentChatId;
    protected boolean isHistoryMessage;
    private HistoryId historyId;
    private boolean readByOperator;
    private boolean canBeEdited;

    public MessageImpl(
            @NonNull String serverUrl,
            @NonNull Id id,
            @Nullable Operator.Id operatorId,
            @Nullable String avatarUrl,
            @NonNull String senderName,
            @NonNull Type type,
            @NonNull String text,
            long timeMicros,
            @Nullable Attachment attachment,
            String internalId,
            @Nullable String rawText,
            boolean isHistoryMessage,
            @Nullable String data,
            boolean readByOperator,
            boolean canBeEdited,
            @Nullable Keyboard keyboard,
            @Nullable KeyboardRequest keyboardRequest
    ) {
        serverUrl.getClass(); // NPE
        id.getClass(); // NPE
        senderName.getClass(); // NPE
        type.getClass(); // NPE
        text.getClass(); // NPE

        if (isHistoryMessage) {
            historyId = new HistoryId(internalId, timeMicros);
        } else {
            this.currentChatId = internalId;
        }

        this.serverUrl = serverUrl;
        this.id = id;
        this.operatorId = operatorId;
        this.avatarUrl = avatarUrl;
        this.senderName = senderName;
        this.type = type;
        this.text = text;
        this.timeMicros = timeMicros;
        this.attachment = attachment;
        this.rawText = rawText;
        this.isHistoryMessage = isHistoryMessage;
        this.data = data;
        this.readByOperator = readByOperator;
        this.canBeEdited = canBeEdited;
        this.keyboard = keyboard;
        this.keyboardRequest = keyboardRequest;
    }

    @Override
    public boolean isReadByOperator() {
        return readByOperator || type != Type.VISITOR && type != Type.FILE_FROM_VISITOR;
    }

    @Override
    public boolean canBeEdited() {
        return canBeEdited;
    }

    @Override
    public Keyboard getKeyboard() {
        return keyboard;
    }

    @Override
    public KeyboardRequest getKeyboardRequest() {
        return keyboardRequest;
    }

    public void setReadByOperator(boolean read) {
        this.readByOperator = read;
    }

    @Nullable
    public String getAvatarUrlLastPart() {
        return avatarUrl;
    }

    @Nullable
    public String getData() {
        return data;
    }

    @Nullable
    public String getRawText() {
        return rawText;
    }

    @NonNull
    public MessageSource getSource() {
        return isHistoryMessage ? MessageSource.HISTORY : MessageSource.CURRENT_CHAT;
    }

    @NonNull
    @Override
    public Id getId() {
        return id;
    }

    @NonNull
    @Override
    public String getCurrentChatId() {
        return currentChatId;
    }

    @Nullable
    @Override
    public Operator.Id getOperatorId() {
        return operatorId;
    }

    @Nullable
    @Override
    public String getSenderAvatarUrl() {
        return avatarUrl == null ? null : serverUrl + avatarUrl;
    }

    @NonNull
    @Override
    public String getSenderName() {
        return senderName;
    }

    @NonNull
    @Override
    public Type getType() {
        return type;
    }

    @NonNull
    @Override
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Message && this.getId().equals(((Message) o).getId());
    }

    @Override
    public long getTime() {
        return timeMicros / 1000;
    }

    @Nullable
    @Override
    public Attachment getAttachment() {
        return attachment;
    }

    @NonNull
    @Override
    public SendStatus getSendStatus() {
        return sendStatus;
    }

    @Override
    public long getTimeMicros() {
        return timeMicros;
    }

    @NonNull
    public HistoryId getHistoryId() {
        if (historyId == null) {
            throw new IllegalStateException();
        }
        return historyId;
    }

    @NonNull
    public String getIdInCurrentChat() {
        if (currentChatId == null) {
            throw new IllegalStateException();
        }
        return currentChatId;
    }

    @NonNull
    public String getPrimaryId() {
        return getSource().isHistory() ? historyId.getDbId() : currentChatId;
    }

    @NonNull
    public MessageImpl transferToHistory(@History MessageImpl historyEquivalent) {
        if (!isContentEquals(this, historyEquivalent)) {
            historyEquivalent.setSecondaryCurrentChat(this);
            return historyEquivalent;
        }
        setSecondaryHistory(historyEquivalent);
        invert();
        return this;
    }

    @NonNull
    public MessageImpl transferToCurrentChat(@CurrentChat MessageImpl currentChatEquivalent) {
        if (!isContentEquals(this, currentChatEquivalent)) {
            currentChatEquivalent.setSecondaryHistory(this);
            return currentChatEquivalent;
        }
        setSecondaryCurrentChat(currentChatEquivalent);
        invert();
        return this;
    }

    public void setCanBeEdited(boolean canBeEdited) {
        this.canBeEdited = canBeEdited;
    }

    public void setSecondaryHistory(@History MessageImpl historyEquivalent) {
        if (getSource().isHistory()) {
            throw new IllegalStateException();
        }
        if (!historyEquivalent.getSource().isHistory()) {
            throw new IllegalArgumentException();
        }
        historyId = historyEquivalent.getHistoryId();
    }

    public void setSecondaryCurrentChat(@CurrentChat MessageImpl currentChatEquivalent) {
        if (!getSource().isHistory()) {
            throw new IllegalStateException();
        }
        if (currentChatEquivalent.getSource().isHistory()) {
            throw new IllegalArgumentException();
        }
        currentChatId = currentChatEquivalent.currentChatId;
    }

    public void invert() {
        if (historyId == null || currentChatId == null) {
            throw new IllegalStateException();
        }
        isHistoryMessage = !isHistoryMessage;

    }

    public boolean hasHistoryComponent() {
        return historyId != null;
    }

    public static boolean isContentEquals(MessageImpl m1, MessageImpl m2) {
        return
                m1.id.toString().equals(m2.id.toString())
                        && InternalUtils.equals(m1.operatorId == null
                                ? null
                                : m1.operatorId.toString(),
                        m2.operatorId == null
                                ? null
                                : m2.operatorId.toString())
                        && InternalUtils.equals(m1.avatarUrl, m2.avatarUrl)
                        && m1.senderName.equals(m2.senderName)
                        && m1.type.equals(m2.type)
                        && m1.text.equals(m2.text)
                        && m1.timeMicros == m2.timeMicros
                        && InternalUtils.equals(m1.rawText, m2.rawText)
                        && m1.isReadByOperator() == m2.isReadByOperator()
                        && m1.canBeEdited == m2.canBeEdited;
    }

    @Override
    public String toString() {
        return "MessageImpl{"
                + "\nserverUrl='" + serverUrl + '\''
                + ", \nid=" + id
                + ", \noperatorId=" + operatorId
                + ", \navatarUrl='" + avatarUrl + '\''
                + ", \nsenderName='" + senderName + '\''
                + ", \ntype=" + type
                + ", \ntext='" + text + '\''
                + ", \ntimeMicros=" + timeMicros
                + ", \nattachment=" + attachment
                + ", \nrawText='" + rawText + '\''
                + ", \nisHistoryMessage=" + isHistoryMessage
                + ", \ncurrentChatId='" + currentChatId + '\''
                + ", \nhistoryId=" + historyId + '\''
                + ", \ncanBeEdited=" + canBeEdited
                + "\n}";
    }

    public static class AttachmentImpl implements Message.Attachment {
        private final @NonNull String url;
        private final long size;
        private final String filename;
        private final String contentType;
        private final @Nullable ImageInfo imageInfo;

        public AttachmentImpl(@NonNull String url,
                              long size,
                              String filename,
                              String contentType,
                              @Nullable ImageInfo imageInfo) {
            url.getClass(); // NPE
            filename.getClass(); // NPE
            contentType.getClass(); // NPE
            this.url = url;
            this.size = size;
            this.filename = filename;
            this.contentType = contentType;
            this.imageInfo = imageInfo;
        }

        @NonNull
        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public long getSize() {
            return size;
        }

        @NonNull
        @Override
        public String getFileName() {
            return filename;
        }

        @NonNull
        @Override
        public String getContentType() {
            return contentType;
        }

        @Nullable
        @Override
        public ImageInfo getImageInfo() {
            return imageInfo;
        }
    }

    public static class ImageInfoImpl implements ImageInfo {
        private final @NonNull String thumbUrl;
        private final int width;
        private final int height;

        public ImageInfoImpl(@NonNull String thumbUrl, int width, int height) {
            thumbUrl.getClass(); // NPE
            this.thumbUrl = thumbUrl;
            this.width = width;
            this.height = height;
        }

        @NonNull
        @Override
        public String getThumbUrl() {
            return thumbUrl;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }
    }

    public enum MessageSource {
        HISTORY,
        CURRENT_CHAT;

        public boolean isHistory() {
            return this == HISTORY;
        }

        public boolean isCurrentChat() {
            return this == CURRENT_CHAT;
        }

        public void assertHistory() {
            if (!isHistory()) {
                throw new IllegalStateException();
            }
        }

        public void assertCurrentChat() {
            if (!isCurrentChat()) {
                throw new IllegalStateException();
            }
        }
    }

    public static class KeyboardImpl implements Keyboard {
        private final @NonNull String state;
        private final @Nullable List<List<KeyboardButtons>> keyboardButton;
        private final @Nullable KeyboardResponse keyboardResponse;

        public KeyboardImpl(
                @NonNull String state,
                @Nullable List<List<KeyboardButtons>> keyboardButton,
                @Nullable KeyboardResponse keyboardResponse) {
            this.state = state;
            this.keyboardButton = keyboardButton;
            this.keyboardResponse = keyboardResponse;
        }

        @Nullable
        @Override
        public List<List<KeyboardButtons>> getButtons() {
            return keyboardButton;
        }

        @NonNull
        @Override
        public String getState() {
            return state;
        }

        @Nullable
        @Override
        public KeyboardResponse getKeyboardResponse() {
            return keyboardResponse;
        }
    }

    public static class KeyboardButtonsImpl implements KeyboardButtons {
        private final @NonNull String id;
        private final @NonNull String text;

        public KeyboardButtonsImpl(@NonNull String id, @NonNull String text) {
            this.id = id;
            this.text = text;
        }

        @NonNull
        @Override
        public String getId() {
            return id;
        }

        @NonNull
        @Override
        public String getText() {
            return text;
        }
    }

    public static class KeyboardResponseImpl implements KeyboardResponse {
        private final @NonNull String buttonId;
        private final @NonNull String messageId;

        public KeyboardResponseImpl(
                @NonNull String buttonId,
                @NonNull String messageId) {
            this.buttonId = buttonId;
            this.messageId = messageId;
        }

        @NonNull
        @Override
        public String getButtonId() {
            return buttonId;
        }

        @NonNull
        @Override
        public String getMessageId() {
            return messageId;
        }
    }

    public static class KeyboardRequestImpl implements Message.KeyboardRequest {
        private final @NonNull KeyboardButtons button;
        private final @NonNull String messageId;

        public KeyboardRequestImpl(
                @NonNull KeyboardButtons button,
                @NonNull String messageId) {
            this.button = button;
            this.messageId = messageId;
        }

        @NonNull
        @Override
        public KeyboardButtons getButtons() {
            return button;
        }

        @NonNull
        @Override
        public String getMessageId() {
            return messageId;
        }
    }
}
