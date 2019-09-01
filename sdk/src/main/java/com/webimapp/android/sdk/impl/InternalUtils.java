package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimPushNotification;
import com.webimapp.android.sdk.impl.backend.WebimClient;
import com.webimapp.android.sdk.impl.backend.WebimInternalLog;
import com.webimapp.android.sdk.impl.items.FileParametersItem;
import com.webimapp.android.sdk.impl.items.KeyboardItem;
import com.webimapp.android.sdk.impl.items.KeyboardRequestItem;
import com.webimapp.android.sdk.impl.items.MessageItem;

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
            if (push == null
                    || push.getType() == null
                    || push.getEvent() == null
                    || push.getParams() == null) {
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
            //noinspection ConstantConditions
            if (push == null
                    || push.getType() == null
                    || push.getEvent() == null
                    || params == null) {
                return null;
            }
            int indexOfId;
            switch (push.getType()) {
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

    public static String toJson(Object obj) {
        return gson.toJson(obj);
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
    public static Message.Attachment getAttachment(@NonNull String serverUrl,
                                                   @NonNull MessageItem messageItem,
                                                   @NonNull WebimClient client) {
        if (messageItem.getType() == MessageItem.WMMessageKind.FILE_FROM_VISITOR
                || messageItem.getType() == MessageItem.WMMessageKind.FILE_FROM_OPERATOR) {
            if (client.getAuthData() != null) {
                try {
                    return getAttachment(serverUrl, messageItem.getMessage(), client);
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
            } else {
                WebimInternalLog.getInstance().log("Await pageId initialisation to create file URL",
                        Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING
                );
            }
        }

        return null;
    }

    @NonNull
    public static Message.Attachment getAttachment(@NonNull String serverUrl,
                                                   @NonNull String text,
                                                   @NonNull WebimClient client) {
        try {
            FileParametersItem fileParams = InternalUtils.fromJson(text, FileParametersItem.class);
            String fileUrl = HttpUrl.parse(serverUrl).toString().replaceFirst("/*$", "/")
                    + "l/v/m/download/"
                    + fileParams.getGuid() + "/"
                    + URLEncoder.encode(fileParams.getFilename(), "utf-8") + "?";
            if (client.getAuthData() != null) {
                String pageId = client.getAuthData().getPageId();
                long expires = currentTimeSeconds() + ATTACHMENT_URL_EXPIRES_PERIOD;
                String data = fileParams.getGuid() + expires;
                String key = client.getAuthData().getAuthToken();
                String hash = sha256(data, key);
                fileUrl += "page-id=" + pageId + "&expires=" + expires + "&hash=" + hash;
            }
            return new MessageImpl.AttachmentImpl(fileUrl,
                    fileParams.getSize(),
                    fileParams.getFilename(),
                    fileParams.getContentType(),
                    extractImageData(fileParams, fileUrl));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
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
                                                      @Nullable String fileUrl) {
        if (fileParams == null || fileUrl == null || fileParams.getImageParams() == null) {
            return null;
        }
        FileParametersItem.WMImageParams.WMImageSize size = fileParams.getImageParams().getSize();
        if (size == null) {
            return null;
        }
        String url = fileUrl + "&thumb=android";

        return new MessageImpl.ImageInfoImpl(url, size.getWidth(), size.getHeight());
    }

    public static <T> T getKeyboard(String data, boolean isHistoryMessage, Type mapType) {
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
                        keyboardItem.getState(),
                        extractButtons(keyboardItem),
                        extractResponse(keyboardItem.getResponse()))
                : null;
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

    private static Message.KeyboardButtons extractButton(KeyboardRequestItem.Button keyboardRequest) {
        return new MessageImpl.KeyboardButtonsImpl(
                keyboardRequest.getId(),
                keyboardRequest.getText());
    }

    @Nullable
    public static Message.Quote getQuote(@NonNull String serverUrl,
                                         @Nullable MessageItem.Quote quote,
                                         @NonNull WebimClient client) {
        if (quote != null) {
            Message.Attachment attachment = null;
            String quoteText = null;
            String quoteSenderName =  null;
            String quoteId = null;
            String quoteAuthorId = null;
            long quoteTimeSeconds = 0;
            Message.Type quoteState = null;
            if ((quote.getState() == MessageItem.Quote.State.FILLED)) {
                if ((quote.getType() == MessageItem.WMMessageKind.FILE_FROM_VISITOR)
                        || (quote.getType() == MessageItem.WMMessageKind.FILE_FROM_OPERATOR)) {
                    attachment = extractAttachment(serverUrl, quote, client);
                }
                quoteText = quote.getText();
                quoteAuthorId = quote.getAuthorId();
                quoteId = quote.getId();
                quoteState = toPublicMessageType(quote.getType());
                quoteSenderName = quote.getName();
                quoteTimeSeconds = quote.getTimeSeconds();
            }
            return new MessageImpl.QuoteImpl(
                    attachment,
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

    private static Message.Attachment extractAttachment(
            @NonNull String serverUrl,
            @NonNull MessageItem.Quote quotedMessage,
            @NonNull WebimClient client) {
        MessageItem messageItem = new MessageItem();
        messageItem.setMessage(percentDecode(quotedMessage.getText()));
        messageItem.setType(quotedMessage.getType());
        messageItem.setId(quotedMessage.getId());
        return getAttachment(serverUrl, messageItem, client);
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
}
