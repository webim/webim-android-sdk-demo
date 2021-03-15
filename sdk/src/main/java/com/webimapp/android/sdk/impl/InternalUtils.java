package com.webimapp.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.Survey;
import com.webimapp.android.sdk.UploadedFile;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimPushNotification;
import com.webimapp.android.sdk.impl.backend.WebimClient;
import com.webimapp.android.sdk.impl.backend.WebimInternalLog;
import com.webimapp.android.sdk.impl.items.FileItem;
import com.webimapp.android.sdk.impl.items.FileParametersItem;
import com.webimapp.android.sdk.impl.items.KeyboardItem;
import com.webimapp.android.sdk.impl.items.KeyboardRequestItem;
import com.webimapp.android.sdk.impl.items.MessageItem;
import com.webimapp.android.sdk.impl.items.StickerItem;
import com.webimapp.android.sdk.impl.items.SurveyItem;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;

public final class InternalUtils {
    private static final Gson gson = new Gson();
    private static final Long ATTACHMENT_URL_EXPIRES_PERIOD = 5L * 60L; // 5 minutes

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static String createServerUrl(String accountName) {
        return (accountName == null || accountName.contains("://"))
                ? accountName
                : String.format("https://%s.webim.ru", accountName);
    }

    @Nullable
    public static WebimPushNotification parseFcmPushNotification(@NonNull String message) {
        try {
            WebimPushNotification push
                    = fromJson(message, WebimPushNotificationImpl.class);
            //noinspection ConstantConditions
            if (push == null || push.getEvent() == null) {
                return null;
            }
            return push;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    @Nullable
    public static WebimPushNotification parseFcmPushNotification(@NonNull String message,
                                                                 @NonNull String visitorId) {
        try {
            WebimPushNotification push
                    = fromJson(message, WebimPushNotificationImpl.class);
            List<String> params = push.getParams();
            WebimPushNotification.NotificationType type = push.getType();
            //noinspection ConstantConditions
            if (push == null || push.getEvent() == null) {
                return null;
            }
            if (push.getEvent().equals("del")) {
                return push;
            }
            if (type == null || params == null) {
                return null;
            }

            int indexOfId;
            switch (type) {
                case OPERATOR_ACCEPTED:
                    indexOfId = 1;
                    break;
                case OPERATOR_FILE:
                case OPERATOR_MESSAGE:
                    indexOfId = 2;
                    break;
                 default:
                    indexOfId = 0;
            }
            if (params.size() <= indexOfId) {
                return push;
            }
            return params.get(indexOfId).equals(visitorId) ? push : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> cls) throws JsonSyntaxException {
        return gson.fromJson(json, cls);
    }

    public static <T> T fromJson(JsonElement je, Type cls) throws JsonSyntaxException {
        return gson.fromJson(je, cls);
    }

    public static <T extends Comparable<T>> int compare(T x, T y) {
        return x.compareTo(y);
    }

    @NonNull
    public static Message.Type toPublicMessageType(@NonNull MessageItem.WMMessageKind kind) {
        switch (kind) {
            case ACTION_REQUEST:
                return Message.Type.ACTION_REQUEST;
            case FILE_FROM_OPERATOR:
                return Message.Type.FILE_FROM_OPERATOR;
            case FILE_FROM_VISITOR:
                return Message.Type.FILE_FROM_VISITOR;
            case INFO:
                return Message.Type.INFO;
            case OPERATOR:
                return Message.Type.OPERATOR;
            case OPERATOR_BUSY:
                return Message.Type.OPERATOR_BUSY;
            case VISITOR:
                return Message.Type.VISITOR;
            case CONTACT_REQUEST:
                return Message.Type.CONTACT_REQUEST;
            case KEYBOARD:
                return Message.Type.KEYBOARD;
            case KEYBOARD_RESPONCE:
                return Message.Type.KEYBOARD_RESPONSE;
            case STICKER_VISITOR:
                return Message.Type.STICKER_VISITOR;
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    @Nullable
    public static Operator.Id getOperatorId(@NonNull MessageItem messageItem) {
        switch (messageItem.getType()) {
            case OPERATOR:
            case CONTACT_REQUEST:
            case FILE_FROM_OPERATOR:
                return messageItem.getSenderId() == null
                        ? null
                        : StringId.forOperator(messageItem.getSenderId());
            default:
                return null;
        }
    }

    @Nullable
    public static String getAvatarUrl(@NonNull String serverUrl, @NonNull MessageItem messageItem) {
        return messageItem.getSenderAvatarUrl() == null
                ? null
                : serverUrl + messageItem.getSenderAvatarUrl();
    }

    @Nullable
    public static Message.Attachment getAttachment(
        @NonNull MessageItem messageItem,
        @NonNull FileUrlCreator fileUrlCreator) {
        if (messageItem.getType() == MessageItem.WMMessageKind.FILE_FROM_VISITOR
                || messageItem.getType() == MessageItem.WMMessageKind.FILE_FROM_OPERATOR) {
            try {
                Object json = messageItem.getData();
                String string = new Gson().toJson(json);
                Type mapType = new TypeToken<FileItem>(){}.getType();
                FileItem fileItem = gson.fromJson(string, mapType);
                return (fileItem != null)
                    ? getAttachment(fileItem.getFile(), fileUrlCreator)
                    : getAttachment(messageItem.getMessage(), fileUrlCreator);
            } catch (Exception e) {
                WebimInternalLog.getInstance().log(
                    "Failed to parse file params for message: "
                        + messageItem.getId()
                        + ", "
                        + messageItem.getClientSideId()
                        + ", text: "
                        + messageItem.getMessage()
                        + "."
                        + e,
                    Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR
                );
            }
        }
        return null;
    }

    @Nullable
    private static Message.Attachment getAttachment(
        @Nullable FileItem.File file,
        @NonNull FileUrlCreator fileUrlCreator) {
        if (file != null) {
            Message.FileInfo fileInfo = getFileInfo(file, fileUrlCreator);
            List<Message.FileInfo> filesInfo = new ArrayList<>();
            filesInfo.add(fileInfo);
            return new MessageImpl.AttachmentImpl(
                    file.getDownloadProgress(),
                    file.getErrorType(),
                    file.getErrorMessage(),
                    fileInfo,
                    filesInfo,
                    getFileState(file.getState()));
        } else {
            return null;
        }
    }

    @NonNull
    private static Message.Attachment getAttachment(
        @NonNull String message,
        @NonNull FileUrlCreator fileUrlCreator) throws JSONException {
        List<Message.FileInfo> filesInfo = new ArrayList<>();
        Message.FileInfo fileInfo;
        Boolean messageIsArray = new JsonParser().parse(message).isJsonArray();
        if (messageIsArray) {
            JSONArray jsonArray = new JSONArray(message);
            fileInfo = getFileInfo(jsonArray.get(0).toString(), fileUrlCreator);
            filesInfo.add(fileInfo);
            for (int i = 1; i < jsonArray.length(); i++) {
                filesInfo.add(getFileInfo(jsonArray.get(i).toString(), fileUrlCreator));
            }
        } else {
            fileInfo = getFileInfo(message, fileUrlCreator);
            filesInfo.add(fileInfo);
        }
        return new MessageImpl.AttachmentImpl(
                100,
                "",
                "",
                fileInfo,
                filesInfo,
                Message.Attachment.AttachmentState.READY);
    }

    @NonNull
    private static Message.FileInfo getFileInfo(
        @NonNull FileItem.File file,
        @NonNull FileUrlCreator fileUrlCreator) {
        FileParametersItem fileParams = file.getProperties();
        if (file.getState() == FileItem.File.FileState.READY) {
            return getFileInfoImpl(fileParams, fileUrlCreator);
        } else {
            return new MessageImpl.FileInfoImpl(
                    "",
                    (fileParams != null) ? fileParams.getFilename() : "",
                    null,
                    0,
                    null,
                    fileParams != null ? fileParams.getGuid() : "",
                    fileUrlCreator);
        }
    }

    @NonNull
    private static Message.FileInfo getFileInfo(@NonNull String json,
                                                @NonNull FileUrlCreator fileUrlCreator) {
        FileParametersItem fileParams = fromJson(json, FileParametersItem.class);
        return getFileInfoImpl(fileParams, fileUrlCreator);
    }

    @NonNull
    private static MessageImpl.FileInfoImpl getFileInfoImpl(
        @NonNull FileParametersItem fileParams,
        @NonNull FileUrlCreator fileUrlCreator) {
        String fileUrl = fileUrlCreator.createFileUrl(fileParams.getFilename(), fileParams.getGuid(), false);
        return new MessageImpl.FileInfoImpl(
            fileParams.getContentType(),
            fileParams.getFilename(),
            extractImageData(fileParams, fileUrlCreator),
            fileParams.getSize(),
            fileUrl,
            fileParams.getGuid(),
            fileUrlCreator);
    }

    @NonNull
    public static UploadedFile getUploadedFile(@NonNull String response) {
        FileParametersItem fileParams = fromJson(response, FileParametersItem.class);
        return new UploadedFileImpl(
                fileParams.getSize(),
                fileParams.getGuid(),
                fileParams.getContentType(),
                fileParams.getFilename(),
                fileParams.getVisitorId(),
                fileParams.getClientContentType());
    }

    @NonNull
    private static Message.Attachment.AttachmentState getFileState (@NonNull FileItem.File.FileState fileState) {
        switch (fileState) {
            case READY:
                return Message.Attachment.AttachmentState.READY;
            case UPLOAD:
                return Message.Attachment.AttachmentState.UPLOAD;
            case ERROR:
            default:
                return Message.Attachment.AttachmentState.ERROR;
        }
    }

    private static String sha256(String data, String key) {
        StringBuilder hash = new StringBuilder();
        try {
            final Charset asciiCs = Charset.forName("US-ASCII");
            final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            final SecretKeySpec secret_key
                    = new javax.crypto.spec.SecretKeySpec(asciiCs.encode(key).array(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            final byte[] mac_data = sha256_HMAC.doFinal(asciiCs.encode(data).array());
            for (final byte element : mac_data) {
                hash.append(Integer.toString((element & 0xff) + 0x100, 16).substring(1));
            }
        } catch (Exception ignored) { }
        return hash.toString();
    }

    private static Long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    @Nullable
    private static Message.ImageInfo extractImageData(@Nullable FileParametersItem fileParams,
                                                      @NonNull FileUrlCreator fileUrlCreator) {
        if (fileParams == null || fileParams.getImageParams() == null) {
            return null;
        }
        FileParametersItem.WMImageParams.WMImageSize size = fileParams.getImageParams().getSize();
        if (size == null) {
            return null;
        }
        String url = fileUrlCreator.createFileUrl(fileParams.getFilename(), fileParams.getGuid(), true);

        return new MessageImpl.ImageInfoImpl(
            url,
            size.getWidth(),
            size.getHeight(),
            fileParams.getFilename(),
            fileParams.getGuid(),
            fileUrlCreator
        );
    }

    public static <T> T getItem(String data, boolean isHistoryMessage, Type mapType) {
        if (!isHistoryMessage) {
            return gson.fromJson(data, mapType);
        } else {
            JsonElement jsonElement = gson.fromJson(data, JsonElement.class);
            return gson.fromJson(jsonElement, mapType);
        }
    }

    public static Message.Keyboard getKeyboardButton(@Nullable KeyboardItem keyboardItem) {
        return keyboardItem != null
                ? new MessageImpl.KeyboardImpl(
                        getKeyboardState(keyboardItem.getState()),
                        extractButtons(keyboardItem),
                        extractResponse(keyboardItem.getResponse()))
                : null;
    }

    private static Message.Keyboard.State getKeyboardState(KeyboardItem.State keyboardState) {
        switch (keyboardState) {
            case PENDING:
                return Message.Keyboard.State.PENDING;
            case COMPLETED:
                return Message.Keyboard.State.COMPLETED;
            default:
                return Message.Keyboard.State.CANCELLED;
        }
    }

    @Nullable
    private static List<List<Message.KeyboardButtons>> extractButtons(@Nullable KeyboardItem keyboardItem) {
        if (keyboardItem != null && keyboardItem.getButtons() != null) {
            return getKeyboardButtons(keyboardItem);
        } else {
            return null;
        }
    }

    private static List<List<Message.KeyboardButtons>> getKeyboardButtons(KeyboardItem keyboardItem) {
        List<List<Message.KeyboardButtons>> keyboardButtons = new ArrayList<>();
        for (List<KeyboardItem.Buttons> buttonList : keyboardItem.getButtons()) {
            keyboardButtons.add(getButtons(buttonList));
        }
        return keyboardButtons;
    }

    private static List<Message.KeyboardButtons> getButtons(List<KeyboardItem.Buttons> buttonList) {
        List<Message.KeyboardButtons> keyboardButtonsList = new ArrayList<>();
        for (KeyboardItem.Buttons buttons : buttonList) {
            Message.KeyboardButtons button = new MessageImpl.KeyboardButtonsImpl(
                    buttons.getId(),
                    buttons.getText());
            keyboardButtonsList.add(button);
        }
        return keyboardButtonsList;
    }

    @Nullable
    private static Message.KeyboardResponse extractResponse(@Nullable KeyboardItem.Response keyboardItem) {
        return keyboardItem != null
                ? new MessageImpl.KeyboardResponseImpl(
                        keyboardItem.getButtonId(),
                        keyboardItem.getMessageId())
                : null;
    }

    @Nullable
    public static Message.KeyboardRequest getKeyboardRequest(@Nullable KeyboardRequestItem keyboardRequestItem) {
        return keyboardRequestItem != null
                ? new MessageImpl.KeyboardRequestImpl(
                        extractButton(keyboardRequestItem.getButton()),
                        keyboardRequestItem.getRequest().getMessageId())
                : null;
    }

    public static Message.Sticker getSticker(StickerItem stickerItem) {
        return new MessageImpl.StickerImpl(stickerItem.getStickerId());
    }

    private static Message.KeyboardButtons extractButton(KeyboardRequestItem.Button keyboardRequest) {
        return new MessageImpl.KeyboardButtonsImpl(
                keyboardRequest.getId(),
                keyboardRequest.getText());
    }

    @Nullable
    public static Message.Quote getQuote(
        @Nullable MessageItem.Quote quote,
        @NonNull FileUrlCreator fileUrlCreator) {
        if (quote != null) {
            Message.FileInfo fileInfo = null;
            String quoteText = null;
            String quoteSenderName =  null;
            String quoteId = null;
            String quoteAuthorId = null;
            long quoteTimeSeconds = 0;
            Message.Type quoteState = null;
            if ((quote.getState() == MessageItem.Quote.State.FILLED)) {
                MessageItem.Quote.QuotedMessage quotedMessage = quote.getMessage();
                if ((quotedMessage.getKind() == MessageItem.WMMessageKind.FILE_FROM_VISITOR)
                    || (quotedMessage.getKind() == MessageItem.WMMessageKind.FILE_FROM_OPERATOR)) {
                    fileInfo = extractFileInfo(quotedMessage, fileUrlCreator);
                }
                quoteText = quotedMessage.getText();
                quoteAuthorId = quotedMessage.getAuthorId();
                quoteId = quotedMessage.getId();
                quoteState = toPublicMessageType(quotedMessage.getKind());
                quoteSenderName = quotedMessage.getName();
                quoteTimeSeconds = quotedMessage.getTsSeconds();
            }
            return new MessageImpl.QuoteImpl(
                    fileInfo,
                    quoteAuthorId,
                    quoteId,
                    quoteState,
                    quoteSenderName,
                    extractState(quote.getState()),
                    quoteText,
                    quoteTimeSeconds);
        }
        return null;
    }

    private static Message.Quote.State extractState(MessageItem.Quote.State state) {
        switch (state) {
            case PENDING:
                return Message.Quote.State.PENDING;
            case FILLED:
                return Message.Quote.State.FILLED;
            case NOT_FOUND:
            default:
                return Message.Quote.State.NOT_FOUND;
        }
    }

    private static Message.FileInfo extractFileInfo(
        @NonNull MessageItem.Quote.QuotedMessage quotedMessage,
        @NonNull FileUrlCreator fileUrlCreator) {
        MessageItem messageItem = new MessageItem();
        messageItem.setMessage(percentDecode(quotedMessage.getText()));
        messageItem.setType(quotedMessage.getKind());
        messageItem.setId(quotedMessage.getId());
        Message.Attachment attachment = getAttachment(messageItem, fileUrlCreator);
        if (attachment != null) {
            MessageImpl.FileInfoImpl fileInfo = (MessageImpl.FileInfoImpl) attachment.getFileInfo();
            return new MessageImpl.FileInfoImpl(
                    fileInfo.getContentType(),
                    fileInfo.getFileName(),
                    fileInfo.getImageInfo(),
                    fileInfo.getSize(),
                    fileInfo.getUrl(),
                    fileInfo.getGuid(),
                    fileUrlCreator
            );
        } else {
            return null;
        }
    }

    private static String percentDecode(String text) {
        if ((text == null) || text.isEmpty()) {
            return text;
        }
        Map<String, String> charactersForDecode = new HashMap<>();
        charactersForDecode.put("%0A", "\n");
        charactersForDecode.put("%22", "\"");
        charactersForDecode.put("%5C", "\\");
        charactersForDecode.put("%25", "%");

        for (String key : charactersForDecode.keySet()) {
            text = text.replace(key, charactersForDecode.get(key));
        }
        return text;
    }

    public static Survey.Question.Type getQuestionType(SurveyItem.Question.Type type) {
        switch (type) {
            case STARS:
                return SurveyImpl.Question.Type.STARS;
            case RADIO:
                return SurveyImpl.Question.Type.RADIO;
            case COMMENT:
            default:
                return SurveyImpl.Question.Type.COMMENT;
        }
    }
}
