package com.webimapp.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.Operator;

import java.util.List;

public class MessageImpl implements Message, TimeMicrosHolder {

    protected final @Nullable String avatarUrl;
    protected final @NonNull Id id;
    protected final @Nullable  String sessionId;
    protected final @Nullable Keyboard keyboard;
    protected final @Nullable KeyboardRequest keyboardRequest;
    protected final @Nullable Operator.Id operatorId;
    protected final @NonNull String senderName;
    protected final @NonNull String serverUrl;
    protected final @NonNull String text;
    protected final long timeMicros;
    protected final @NonNull Type type;

    protected @NonNull SendStatus sendStatus = SendStatus.SENT;

    private final @Nullable Attachment attachment;
    private final @Nullable String rawText;

    private String currentChatId;
    protected boolean isHistoryMessage;
    private HistoryId historyId;
    private boolean readByOperator;
    private boolean canBeEdited;
    private boolean canBeReplied;
    private boolean edited;
    private @Nullable Quote quote;
    private @Nullable Sticker sticker;

    public MessageImpl(
            @NonNull String serverUrl,
            @NonNull Id id,
            @Nullable String sessionId,
            @Nullable Operator.Id operatorId,
            @Nullable String avatarUrl,
            @NonNull String senderName,
            @NonNull Type type,
            @NonNull String text,
            long timeMicros,
            String currentChatId,
            @Nullable String rawText,
            boolean isHistoryMessage,
            @Nullable Attachment attachment,
            boolean readByOperator,
            boolean canBeEdited,
            boolean canBeReplied,
            boolean edited,
            @Nullable Quote quote,
            @Nullable Keyboard keyboard,
            @Nullable KeyboardRequest keyboardRequest,
            @Nullable Sticker sticker
    ) {
        serverUrl.getClass(); // NPE
        id.getClass(); // NPE
        senderName.getClass(); // NPE
        type.getClass(); // NPE
        text.getClass(); // NPE

        if (isHistoryMessage) {
            historyId = new HistoryId(currentChatId, timeMicros);
        }

        this.serverUrl = serverUrl;
        this.id = id;
        this.sessionId = sessionId;
        this.operatorId = operatorId;
        this.avatarUrl = avatarUrl;
        this.senderName = senderName;
        this.type = type;
        this.text = text;
        this.timeMicros = timeMicros;
        this.currentChatId = currentChatId;
        this.rawText = rawText;
        this.isHistoryMessage = isHistoryMessage;
        this.attachment = attachment;
        this.readByOperator = readByOperator;
        this.canBeEdited = canBeEdited;
        this.canBeReplied = canBeReplied;
        this.edited = edited;
        this.quote = quote;
        this.keyboard = keyboard;
        this.keyboardRequest = keyboardRequest;
        this.sticker = sticker;
    }

    @Override
    public boolean isReadByOperator() {
        return readByOperator || type != Type.VISITOR && type != Type.FILE_FROM_VISITOR;
    }

    @Override
    public boolean canBeEdited() {
        return canBeEdited;
    }

    public boolean canBeReplied() {
        return canBeReplied;
    }

    @Override
    public boolean isEdited() {
        return edited;
    }

    @Nullable
    @Override
    public Quote getQuote() {
        return quote;
    }

    @Override
    public Keyboard getKeyboard() {
        return keyboard;
    }

    @Override
    public KeyboardRequest getKeyboardRequest() {
        return keyboardRequest;
    }

    @Nullable
    @Override
    public Sticker getSticker() {
        return sticker;
    }

    public void setReadByOperator(boolean read) {
        this.readByOperator = read;
    }

    @Nullable
    public String getAvatarUrlLastPart() {
        return avatarUrl;
    }

    @Nullable
    public Attachment getAttachment() {
        return attachment;
    }

    @Override
    public boolean isHistoryMessage() {
        return isHistoryMessage;
    }

    @Nullable
    public String getData() {
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

    @Nullable
    @Override
    public String getSessionId() {
        return sessionId;
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

    public void setCanBeReplied(boolean canBeReplied) {
        this.canBeReplied = canBeReplied;
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
                        && m1.canBeReplied == m2.canBeReplied
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
        private int downloadProgress;
        private @Nullable String errorMessage;
        private @Nullable String errorType;
        private @NonNull FileInfo fileInfo;
        private @NonNull List<FileInfo> filesInfo;
        private @NonNull AttachmentState state;

        public AttachmentImpl(int downloadProgress,
                              @Nullable String errorMessage,
                              @Nullable String errorType,
                              @NonNull FileInfo fileInfo,
                              @NonNull List<FileInfo> filesInfo,
                              @NonNull AttachmentState state) {
            this.downloadProgress = downloadProgress;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
            this.fileInfo = fileInfo;
            this.filesInfo = filesInfo;
            this.state = state;
        }

        @Override
        public int getDownloadProgress() {
            return downloadProgress;
        }

        @Override
        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        @Nullable
        public String getErrorType() {
            return errorType;
        }

        @Override
        @NonNull
        public FileInfo getFileInfo() {
            return fileInfo;
        }

        @Override
        @NonNull
        public List<FileInfo> getFilesInfo() { return filesInfo; }

        @Override
        @NonNull
        public AttachmentState getState() {
            return state;
        }
    }

    public static class FileInfoImpl implements Message.FileInfo {
        private final @Nullable String contentType;
        private final @NonNull String filename;
        private final @Nullable ImageInfo imageInfo;
        private final long size;
        private final @Nullable String url;

        public FileInfoImpl(@Nullable String contentType,
                            @NonNull String filename,
                            @Nullable ImageInfo imageInfo,
                            long size,
                            @Nullable String url) {

            this.contentType = contentType;
            this.filename = filename;
            this.imageInfo = imageInfo;
            this.size = size;
            this.url = url;
        }

        @Nullable
        @Override
        public String getContentType() {
            return contentType;
        }

        @NonNull
        @Override
        public String getFileName() {
            return filename;
        }

        @Nullable
        @Override
        public ImageInfo getImageInfo() {
            return imageInfo;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Nullable
        @Override
        public String getUrl() {
            return url;
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

    public static class QuoteImpl implements Message.Quote {
        @Nullable
        private final FileInfo attachment;
        @Nullable
        private final String authorId;
        @Nullable
        private final String id;
        @Nullable
        private final Type type;
        @Nullable
        private final String senderName;
        @NonNull
        private final State status;
        @Nullable
        private final String text;
        private final
        long timeSeconds;

        public QuoteImpl(
                @Nullable FileInfo attachment,
                @Nullable String authorId,
                @Nullable String id,
                @Nullable Type type,
                @Nullable String senderName,
                @NonNull State status,
                @Nullable String text,
                long timeSeconds) {
            this.attachment = attachment;
            this.authorId = authorId;
            this.id = id;
            this.type = type;
            this.senderName = senderName;
            this.status = status;
            this.text = text;
            this.timeSeconds = timeSeconds;
        }

        @Nullable
        @Override
        public FileInfo getMessageAttachment() {
            return attachment;
        }

        @Nullable
        @Override
        public String getAuthorId() {
            return authorId;
        }

        @Nullable
        @Override
        public String getMessageId() {
            return id;
        }

        @Nullable
        @Override
        public String getSenderName() {
            return senderName;
        }

        @Nullable
        @Override
        public String getMessageText() {
            return text;
        }

        @Nullable
        @Override
        public Type getMessageType() {
            return type;
        }

        @Override
        public long getMessageTimestamp() {
            return timeSeconds;
        }

        @NonNull
        @Override
        public State getState() {
            return status;
        }
    }

    public static class KeyboardImpl implements Keyboard {
        private final @NonNull Keyboard.State state;
        private final @Nullable List<List<KeyboardButtons>> keyboardButton;
        private final @Nullable KeyboardResponse keyboardResponse;

        public KeyboardImpl(
                @NonNull Keyboard.State state,
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
        public Keyboard.State getState() {
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

    public static class StickerImpl implements Message.Sticker {
        private int stickerId;

        public StickerImpl(int stickerId) {
            this.stickerId = stickerId;
        }

        @Override
        public int getStickerId() {
            return stickerId;
        }
    }
}
