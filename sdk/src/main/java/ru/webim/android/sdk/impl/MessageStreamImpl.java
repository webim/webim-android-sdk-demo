package ru.webim.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import ru.webim.android.sdk.Department;
import ru.webim.android.sdk.Message;
import ru.webim.android.sdk.MessageListener;
import ru.webim.android.sdk.MessageStream;
import ru.webim.android.sdk.MessageTracker;
import ru.webim.android.sdk.Operator;
import ru.webim.android.sdk.Survey;
import ru.webim.android.sdk.UploadedFile;
import ru.webim.android.sdk.impl.backend.LocationSettingsImpl;
import ru.webim.android.sdk.impl.backend.SendKeyboardErrorListener;
import ru.webim.android.sdk.impl.backend.WebimActions;
import ru.webim.android.sdk.impl.backend.WebimInternalError;
import ru.webim.android.sdk.impl.backend.callbacks.SendOrDeleteMessageInternalCallback;
import ru.webim.android.sdk.impl.backend.callbacks.SurveyFinishCallback;
import ru.webim.android.sdk.impl.backend.callbacks.SurveyQuestionCallback;
import ru.webim.android.sdk.impl.items.AccountConfigItem;
import ru.webim.android.sdk.impl.items.ChatItem;
import ru.webim.android.sdk.impl.items.DepartmentItem;
import ru.webim.android.sdk.impl.items.LocationSettingsItem;
import ru.webim.android.sdk.impl.items.OnlineStatusItem;
import ru.webim.android.sdk.impl.items.RatingItem;
import ru.webim.android.sdk.impl.items.SurveyItem;
import ru.webim.android.sdk.impl.items.VisitSessionStateItem;
import ru.webim.android.sdk.impl.items.delta.DeltaFullUpdate;
import ru.webim.android.sdk.impl.items.requests.AutocompleteRequest;
import ru.webim.android.sdk.impl.items.responses.SearchResponse;

public class MessageStreamImpl implements MessageStream {
    private final AccessChecker accessChecker;
    private final WebimActions actions;
    private final MessageFactories.Mapper<MessageImpl> currentChatMessageMapper;
    private final LocationSettingsHolder locationSettingsHolder;
    private final MessageComposingHandler messageComposingHandler;
    private final MessageHolder messageHolder;
    private final MessageFactories.SendingFactory sendingMessageFactory;
    private @Nullable ChatItem chat;
    private @Nullable Operator currentOperator;
    private String location;
    private List<CurrentOperatorChangeListener> currentOperatorListeners = new ArrayList<>();
    private List<Department> departmentList;
    private List<DepartmentListChangeListener> departmentListChangeListeners = new ArrayList<>();
    private @NonNull VisitSessionStateItem visitSessionState = VisitSessionStateItem.UNKNOWN;
    private boolean isProcessingChatOpen;
    private @NonNull ChatItem.ItemChatState lastChatState = ChatItem.ItemChatState.UNKNOWN;
    private boolean lastOperatorTypingStatus;
    private LocationSettingsChangeListener locationSettingsChangeListener;
    private OnlineStatusChangeListener onlineStatusChangeListener;
    private MessageFactories.OperatorFactory operatorFactory;
    private SurveyFactory surveyFactory;
    private List<OperatorTypingListener> operatorTypingListeners = new ArrayList<>();
    private String serverUrlString;
    private List<ChatStateListener> stateListeners = new ArrayList<>();
    private long unreadByOperatorTimestamp = -1;
    private @Nullable UnreadByOperatorTimestampChangeListener unreadByOperatorTimestampChangeListener;
    private int unreadByVisitorMessageCount = -1;
    private @Nullable UnreadByVisitorMessageCountChangeListener unreadByVisitorMessageCountChangeListener;
    private long unreadByVisitorTimestamp = -1;
    private @Nullable UnreadByVisitorTimestampChangeListener unreadByVisitorTimestampChangeListener;
    private List<VisitSessionStateListener> visitSessionStateListeners = new ArrayList<>();
    private List<RateOperatorListener> rateOperatorListeners = new ArrayList<>();
    private GreetingMessageListener greetingMessageListener;
    private SurveyController surveyController;
    private String onlineStatus = "unknown";

    private AccountConfigItem accountConfigItem;
    private LocationSettingsItem locationSettingsItem;

    MessageStreamImpl(
            String serverUrlString,
            MessageFactories.Mapper<MessageImpl> currentChatMessageMapper,
            MessageFactories.SendingFactory sendingMessageFactory,
            MessageFactories.OperatorFactory operatorFactory,
            SurveyFactory surveyFactory,
            AccessChecker accessChecker,
            WebimActions actions,
            MessageHolder messageHolder,
            MessageComposingHandler messageComposingHandler,
            LocationSettingsHolder locationSettingsHolder,
            String location,
            AccountConfigItem accountConfigItem,
            LocationSettingsItem locationSettingsItem
    ) {
        this.serverUrlString = serverUrlString;
        this.currentChatMessageMapper = currentChatMessageMapper;
        this.sendingMessageFactory = sendingMessageFactory;
        this.operatorFactory = operatorFactory;
        this.surveyFactory = surveyFactory;
        this.accessChecker = accessChecker;
        this.actions = actions;
        this.messageHolder = messageHolder;
        this.messageComposingHandler = messageComposingHandler;
        this.locationSettingsHolder = locationSettingsHolder;
        this.location = location;
        this.accountConfigItem = accountConfigItem;
        this.locationSettingsItem = locationSettingsItem;
    }

    @NonNull
    @Override
    public VisitSessionState getVisitSessionState() {
        return toPublicVisitSessionState(visitSessionState);
    }

    @NonNull
    @Override
    public ChatState getChatState() {
        return toPublicState(lastChatState);
    }

    @Nullable
    @Override
    public Operator getCurrentOperator() {
        return currentOperator;
    }

    @Override
    public int getLastOperatorRating(Operator.Id operatorId) {
        RatingItem rating = ((chat == null)
                ? null
                : chat.getOperatorIdToRating().get(((StringId) operatorId).getInternal()));

        return rating == null ? 0 : internalToRate(rating.getRating());
    }

    @Nullable
    @Override
    public Date getUnreadByOperatorTimestamp() {
        return ((unreadByOperatorTimestamp > 0)
                ? (new Date(unreadByOperatorTimestamp))
                : null);
    }

    @Nullable
    @Override
    public Date getUnreadByVisitorTimestamp() {
        return ((unreadByVisitorTimestamp > 0)
                ? (new Date(unreadByVisitorTimestamp))
                : null);
    }

    public int getUnreadByVisitorMessageCount() {
        return (unreadByVisitorMessageCount > 0) ? unreadByVisitorMessageCount : 0;
    }

    @Nullable
    @Override
    public List<Department> getDepartmentList() {
        return departmentList;
    }

    @Override
    public void startChat(ChatStartedCallback chatStartedCallback) {
        startChatInternally(null, null, null, chatStartedCallback);
    }

    @Override
    public void startChat() {
        startChatInternally(null, null, null, null);
    }

    @Nullable
    @Override
    public String getChatId() {
        return chat != null ? chat.getId() : null;
    }

    @Override
    public void startChatWithDepartmentKey(@Nullable String departmentKey) {
        startChatInternally(null, null, departmentKey, null);
    }

    @Override
    public String startChatWithFirstQuestion(@Nullable String firstQuestion) {
        return startChatInternally(firstQuestion, null, null, null);
    }

    @Override
    public void startChatWithCustomFields(@Nullable String customFields) {
        startChatInternally(null, customFields, null, null);
    }

    @Override
    public String startChatWithDepartmentKeyFirstQuestion(@Nullable String departmentKey,
                                                        @Nullable String firstQuestion) {
        return startChatInternally(firstQuestion, null, departmentKey, null);
    }

    @Override
    public String startChatWithCustomFieldsFirstQuestion(@Nullable String customFields,
                                                       @Nullable String firstQuestion) {
        return startChatInternally(firstQuestion, customFields, null, null);
    }

    @Override
    public String startChatWithCustomFieldsDepartmentKey(@Nullable String customFields,
                                                       @Nullable String departmentKey) {
        return startChatInternally(null, customFields, departmentKey, null);
    }

    @Override
    public String startChatWithFirstQuestionCustomFieldsDepartmentKey(
            @Nullable String firstQuestion,
            @Nullable String customFields,
            @Nullable String departmentKey
    ) {
        return startChatInternally(firstQuestion, customFields, departmentKey, null);
    }

    private String startChatInternally(
        @Nullable String firstQuestion,
        @Nullable String customFields,
        @Nullable String departmentKey,
        @Nullable ChatStartedCallback callback
    ) {

        accessChecker.checkAccess();

        String clientSideId = null;
        if (((lastChatState.isClosed())
            || (visitSessionState == VisitSessionStateItem.OFFLINE_MESSAGE))
            && !isProcessingChatOpen) {
            isProcessingChatOpen = true;
            clientSideId = StringId.generateClientSide();

            actions.startChat(
                clientSideId,
                departmentKey,
                firstQuestion,
                customFields,
                response -> {
                    if (callback != null) {
                        callback.chatStarted();
                    }
                }
            );
        }
        return clientSideId;
    }

    @Override
    public void closeChat() {
        accessChecker.checkAccess();

        if (!lastChatState.isClosed()) {
            actions.closeChat();
        }
    }

    @Override
    public void reactMessage(@NonNull Message message,
                             @NonNull MessageReaction reaction,
                             @Nullable MessageReactionCallback callback) {
        accessChecker.checkAccess();

        if (!message.canVisitorReact() || !InternalUtils.isMessageFromOperator(message)) {
            if (callback != null) {
                callback.onFailure(
                    message.getClientSideId(),
                    new WebimErrorImpl<>(MessageReactionCallback.MessageReactionError.REACTION_FORBIDDEN, null));
            }
            return;
        }
        if (message.getReaction() != null && !message.canVisitorChangeReaction()) {
            if (callback != null) {
                callback.onFailure(
                    message.getClientSideId(),
                    new WebimErrorImpl<>(MessageReactionCallback.MessageReactionError.UNABLE_CHANGE_REACTION, null));
            }
            return;
        }
        actions.reactMessage(message.getClientSideId().toString(), reaction.value, new SendOrDeleteMessageInternalCallback() {
            @Override
            public void onSuccess(String response) {
                if (callback != null) {
                    callback.onSuccess(message.getClientSideId());
                }
            }

            @Override
            public void onFailure(String error) {
                MessageReactionCallback.MessageReactionError webimError = null;
                if (callback != null) {
                    switch (error) {
                        case WebimInternalError.MESSAGE_NOT_FOUND:
                            webimError = MessageReactionCallback.MessageReactionError.MESSAGE_NOT_FOUND;
                            break;
                        case WebimInternalError.NOT_ALLOWED:
                            webimError = MessageReactionCallback.MessageReactionError.NOT_ALLOWED;
                            break;
                        case WebimInternalError.MESSAGE_NOT_OWNED:
                            webimError = MessageReactionCallback.MessageReactionError.MESSAGE_NOT_OWNED;
                            break;
                        default:
                            webimError = MessageReactionCallback.MessageReactionError.UNKNOWN;
                    }
                    callback.onFailure(
                        message.getClientSideId(),
                        new WebimErrorImpl<>(webimError, error));
                }
            }
        });
    }

    @Override
    public void setChatRead() {
        accessChecker.checkAccess();

        if (chat != null) {
            chat.setReadByVisitor(true);
            chat.setUnreadByVisitorTimestamp(0);
        }
        actions.setChatRead();
    }

    @Override
    public void setPrechatFields(String prechatFields) {
        accessChecker.checkAccess();

        actions.setPrechatFields(prechatFields);
    }

    @NonNull
    @Override
    public Message.Id sendMessage(@NonNull String message) {
        return sendMessageInternally(
            message,
            null,
            false,
            null,
            null
        );
    }

    @NonNull
    @Override
    public Message.Id sendMessage(
        @NonNull String message,
        @Nullable String data,
        @Nullable DataMessageCallback dataMessageCallback
    ) {
        return sendMessageInternally(message, data, false, null, dataMessageCallback);
    }

    @NonNull
    @Override
    public Message.Id sendMessage(@NonNull String message, boolean isHintQuestion) {
        return sendMessageInternally(message, null, isHintQuestion, null, null);
    }

    @NonNull
    @Override
    public Message.Id resendMessage(@NonNull Message message, @Nullable final ResendMessageCallback callback) {
        accessChecker.checkAccess();

        Message.SendStatus sendStatus = message.getSendStatus();
        if (!(message instanceof MessageSending) && !(message instanceof MessageFailed)) {
            throw new IllegalArgumentException("Resending message must have status SENDING or FAILED, current: " + sendStatus);
        }

        ResendCallbackMapper resendCallbackMapper = new ResendCallbackMapper();
        switch (message.getType()) {
            case STICKER_VISITOR:
                if (message.getSticker() != null) {
                    Message.Sticker sticker = message.getSticker();
                    sendStickerInternally(sticker.getStickerId(), message.getClientSideId(), resendCallbackMapper.mapStickerSendCallback(callback));
                }
            case FILE_FROM_VISITOR:
                Message.Attachment attachment = message.getAttachment();
                if (attachment != null) {
                    List<Message.FileInfo> filesInfos = attachment.getFilesInfo();
                    if (filesInfos.size() == 1 && filesInfos.get(0).getLocalPath() != null) {
                        Message.FileInfo fileInfo = filesInfos.get(0);
                        String localUri = fileInfo.getLocalPath();
                        String filename = fileInfo.getFileName();
                        String mime = fileInfo.getContentType();
                        sendFileInternally(new File(localUri), filename, mime, message.getClientSideId(), resendCallbackMapper.mapFileSendCallback(callback));
                    } else {
                        sendFilesInternally(filesInfos, message.getClientSideId(), resendCallbackMapper.mapFilesSendCallback(callback));
                    }
                }
                break;
            case VISITOR:
                if (message.getQuote() != null) {
                    Message.Quote quote = message.getQuote();
                    replyMessageInternally(message.getText(), quote, message.getClientSideId());
                } else {
                    sendMessageInternally(message.getText(), null, false, message.getClientSideId(), resendCallbackMapper.mapMessageSendCallback(callback));
                }
                break;
            default:
                throw new IllegalArgumentException("Message type must be FILE_FROM_VISITOR, STICKER_VISITOR or VISITOR, current: " + message.getType());
        }
        return message.getClientSideId();
    }

    @Override
    public boolean replyMessage(
        @NonNull String message,
        @NonNull Message quotedMessage
    ) {
        message.getClass(); // NPE

        accessChecker.checkAccess();

        if(!quotedMessage.canBeReplied()) {
            return false;
        }

        Message.Quote quote = InternalUtils.getQuote(
            quotedMessage.getServerSideId(),
            quotedMessage.getType(),
            quotedMessage.getSenderName(),
            quotedMessage.getText()
        );
        replyMessageInternally(message, quote, null);

        return true;
    }

    private void replyMessageInternally(
        @NonNull String message,
        @NonNull Message.Quote quote,
        @Nullable Message.Id oldClientSideId
        ) {
        final Message.Id messageId = oldClientSideId == null ? StringId.generateForMessage() : oldClientSideId;
        actions.replyMessage(
            message,
            messageId.toString(),
            quote.getQuotedMessageId()
        );

        messageHolder.onSendingMessage(
            sendingMessageFactory.createTextWithQuote(
                messageId,
                message,
                quote
            ),
            oldClientSideId != null
        );
    }

    public void sendKeyboardRequest(@NonNull String messageServerSideId,
                                    @NonNull String buttonId,
                                    @Nullable final SendKeyboardCallback sendKeyboardCallback) {
        final Message.Id messageId = StringId.generateForMessage();
        actions.sendKeyboard(
            messageServerSideId,
                buttonId,
                new SendKeyboardErrorListener() {
                    @Override
                    public void onSuccess() {
                        if (sendKeyboardCallback != null) {
                            sendKeyboardCallback.onSuccess(messageId);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        if (sendKeyboardCallback != null) {
                            sendKeyboardCallback.onFailure(
                                    messageId,
                                    new WebimErrorImpl<>(toPublicSendKeyboardError(error), error)
                            );
                        }
                    }
                });
    }

    @Override
    public void updateWidgetStatus(@NonNull String data) {
        accessChecker.checkAccess();

        actions.updateWidgetStatus(data);
    }

    @Override
    public boolean editMessage(
        @NonNull Message message,
        @NonNull String text,
        @Nullable EditMessageCallback editMessageCallback
    ) {
        return editMessageInternally(message, text, editMessageCallback);
    }

    @Override
    public boolean deleteMessage(@NonNull Message message,
                                 @Nullable DeleteMessageCallback deleteMessageCallback) {
        return deleteMessageInternally(message, deleteMessageCallback);
    }

    @Override
    public void setVisitorTyping(@Nullable String draftMessage) {
        accessChecker.checkAccess();

        messageComposingHandler.setComposingMessage(draftMessage);
    }

    private static int rateToInternal(int rating) {
        switch (rating) {
            case 1:
                return -2;
            case 2:
                return -1;
            case 3:
                return 0;
            case 4:
                return 1;
            case 5:
                return 2;
            default:
                throw new IllegalArgumentException("Rating value should be in range [1,5]; Given: "
                        + rating);
        }
    }

    private static int internalToRate(int rating) {
        switch (rating) {
            case -2:
                return 1;
            case -1:
                return 2;
            case 0:
                return 3;
            case 1:
                return 4;
            case 2:
                return 5;
            default:
                throw new IllegalArgumentException("Rating value should be in range [-2,2]; Given: "
                        + rating);
        }
    }

    private static ChatState toPublicState(ChatItem.ItemChatState state) {
        switch (state) {
            case CHATTING:
                return ChatState.CHATTING;
            case CHATTING_WITH_ROBOT:
                return ChatState.CHATTING_WITH_ROBOT;
            case CLOSED:
                return ChatState.NONE;
            case CLOSED_BY_OPERATOR:
                return ChatState.CLOSED_BY_OPERATOR;
            case CLOSED_BY_VISITOR:
                return ChatState.CLOSED_BY_VISITOR;
            case DELETED:
                return ChatState.DELETED;
            case INVITATION:
                return ChatState.INVITATION;
            case ROUTING:
                return ChatState.ROUTING;
            case QUEUE:
                return ChatState.QUEUE;
            default:
                return ChatState.UNKNOWN;
        }
    }

    static OnlineStatus toPublicOnlineStatus(OnlineStatusItem status) {
        switch (status) {
            case ONLINE:
                return OnlineStatus.ONLINE;
            case BUSY_ONLINE:
                return OnlineStatus.BUSY_ONLINE;
            case OFFLINE:
                return OnlineStatus.OFFLINE;
            case BUSY_OFFLINE:
                return OnlineStatus.BUSY_OFFLINE;
            default:
                return OnlineStatus.UNKNOWN;
        }
    }

    @Override
    public void rateOperator(@NonNull Operator.Id operatorId,
                             int rating,
                             @Nullable RateOperatorCallback rateOperatorCallback) {
        rateOperatorInternally(
                ((StringId) operatorId).getInternal(),
                null,
                rating,
                rateOperatorCallback);
    }

    @Override
    public void rateOperator(@NonNull String operatorId,
                             int rating,
                             @Nullable RateOperatorCallback rateOperatorCallback) {
        rateOperatorInternally(
                operatorId,
                null,
                rating,
                rateOperatorCallback);
    }

    @Override
    public void rateOperator(@NonNull Operator.Id operatorId,
                             @Nullable String note,
                             int rating,
                             @Nullable RateOperatorCallback rateOperatorCallback) {
        rateOperatorInternally(
                ((StringId) operatorId).getInternal(),
                note,
                rating,
                rateOperatorCallback);
    }

    @Override
    public void respondSentryCall(String id) {
        accessChecker.checkAccess();

        actions.respondSentryCall(id);
    }

    @Override
    public void searchMessages(@NonNull String query, @Nullable final SearchMessagesCallback searchCallback) {
        accessChecker.checkAccess();
        actions.searchMessages(query, response -> {
            SearchResponse.SearchResponseData data = response.getData();
            if (data != null && searchCallback != null) {
                if (data.getCount() > 0) {
                    searchCallback.onResult(currentChatMessageMapper.mapAll(data.getMessages()));
                } else {
                    searchCallback.onResult(Collections.<Message>emptyList());
                }
            }
        });
    }

    @NonNull
    @Override
    public Message.Id sendFiles(
        @NonNull List<UploadedFile> uploadedFiles,
        @Nullable final SendFilesCallback sendFilesCallback
    ) {
        accessChecker.checkAccess();

        List<Message.FileInfo> fileInfos = sendingMessageFactory.uploadFilesToFilesInfo(uploadedFiles);
        return sendFilesInternally(fileInfos, null, sendFilesCallback);
    }

    @NonNull
    private Message.Id sendFilesInternally(
        @NonNull List<Message.FileInfo> fileInfos,
        @Nullable Message.Id oldClientSideId,
        @Nullable SendFilesCallback sendFilesCallback
    ) {
        startChatWithDepartmentKeyFirstQuestion(null, null);
        final Message.Id messageId = oldClientSideId == null ? StringId.generateForMessage() : oldClientSideId;

        if (fileInfos.isEmpty()) {
            if (sendFilesCallback != null) {
                sendFilesCallback.onFailure(
                    messageId,
                    new WebimErrorImpl<>(SendFileCallback.SendFileError.FILE_NOT_FOUND, null)
                );
            }
            return messageId;
        }

        if (fileInfos.size() > 10) {
            if (sendFilesCallback != null) {
                sendFilesCallback.onFailure(
                    messageId,
                    new WebimErrorImpl<>(SendFileCallback.SendFileError.MAX_FILES_COUNT_PER_MESSAGE, null)
                );
            }
            return messageId;
        }


        String rawFilesRequest = InternalUtils.toMultiFilesSendString(fileInfos);
        MessageSending messageSending = sendingMessageFactory.createAttachment(messageId, fileInfos);

        actions.sendFiles(
            rawFilesRequest,
            messageId.toString(),
            false,
            new SendOrDeleteMessageInternalCallback() {
                @Override
                public void onSuccess(String response) {
                    if (sendFilesCallback != null) {
                        sendFilesCallback.onSuccess(messageId);
                    }
                }

                @Override
                public void onFailure(String error) {
                    messageHolder.onMessageSendingFailed(messageSending);
                    if (sendFilesCallback != null) {
                        sendFilesCallback.onFailure(
                            messageId,
                            new WebimErrorImpl<>(getFileError(error), error)
                        );
                    }
                }
            });

        messageHolder.onSendingMessage(messageSending, oldClientSideId != null);
        return messageId;
    }

    @NonNull
    @Override
    public Message.Id sendFile(
        @NonNull File file,
        @NonNull String fileName,
        @NonNull String mimeType,
        @Nullable final SendFileCallback callback
    ) {
        file.getClass(); // NPE
        fileName.getClass(); // NPE
        mimeType.getClass(); // NPE

        accessChecker.checkAccess();

        return sendFileInternally(file, fileName, mimeType, null, callback);
    }

    @NonNull
    private Message.Id sendFileInternally(
        @NonNull File file,
        @NonNull String filename,
        @NonNull String mimeType,
        @Nullable Message.Id oldClientSideId,
        @Nullable SendFileCallback callback
    ) {
        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id messageId = oldClientSideId == null ? StringId.generateForMessage() : oldClientSideId;
        if (!patternMatches(filename)) {
            if (callback != null) {
                callback.onFailure(
                    messageId,
                    new WebimErrorImpl<>(
                        SendFileCallback.SendFileError.FILE_NAME_INCORRECT,
                        WebimInternalError.FILE_NAME_INCORRECT
                    )
                );
            }
            return messageId;
        }

        if (file.length() == 0L) {
            if (callback != null) {
                callback.onFailure(messageId, (new WebimErrorImpl<>(
                    SendFileCallback.SendFileError.FILE_IS_EMPTY, null)));
            }
            return messageId;
        }

        MessageSending messageSending = sendingMessageFactory.createFile(messageId, filename, mimeType, file.length(), file.getAbsolutePath());
        messageHolder.onSendingMessage(messageSending, oldClientSideId != null);
        MediaType mime = MediaType.parse(mimeType);
        actions.sendFile(
            RequestBody.create(mime, file),
            filename,
            messageId.toString(),
            new SendOrDeleteMessageInternalCallback() {
                @Override
                public void onSuccess(String response) {
                    if (callback != null) {
                        callback.onSuccess(messageId);
                    }
                }

                @Override
                public void onFailure(String error) {
                    messageHolder.onMessageSendingFailed(messageSending);

                    if (callback != null) {
                        callback.onFailure(messageId, new WebimErrorImpl<>(getFileError(error), error));
                    }
                }
            }
        );

        return messageId;
    }

    @NonNull
    @Override
    public Message.Id sendFile(
        @NonNull FileDescriptor fd,
        @NonNull String fileName,
        @NonNull String mimeType,
        @Nullable SendFileCallback callback
    ) {
        fd.getClass(); // NPE
        fileName.getClass(); // NPE
        mimeType.getClass(); // NPE

        accessChecker.checkAccess();

        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id id = StringId.generateForMessage();
        if (!patternMatches(fileName)) {
            if (callback != null) {
                callback.onFailure(id, (new WebimErrorImpl<>(
                    SendFileCallback.SendFileError.FILE_NAME_INCORRECT,
                    WebimInternalError.FILE_NAME_INCORRECT)));
            }
            return id;
        }

        try (FileInputStream is = new FileInputStream(fd)) {
            if (is.available() == 0) {
                if (callback != null) {
                    callback.onFailure(id, (new WebimErrorImpl<>(
                        SendFileCallback.SendFileError.FILE_IS_EMPTY, null
                    )));
                }
                return id;
            }
        } catch (IOException exc) {
            if (callback != null) {
                callback.onFailure(id, (new WebimErrorImpl<>(
                    SendFileCallback.SendFileError.FILE_IS_EMPTY, null)));
            }
            return id;
        }

        MessageSending messageSending = sendingMessageFactory.createFile(id, fileName, mimeType, 0, null);
        messageHolder.onSendingMessage(messageSending, false);

        actions.sendFile(
            RequestBody.create(fd, MediaType.parse(mimeType)),
            fileName,
            id.toString(),
            new SendOrDeleteMessageInternalCallback() {
                @Override
                public void onSuccess(String response) {
                    if (callback != null) {
                        callback.onSuccess(id);
                    }
                }

                @Override
                public void onFailure(String error) {
                    messageHolder.onMessageSendingFailed(messageSending);

                    if (callback != null) {
                        callback.onFailure(id, new WebimErrorImpl<>(getFileError(error), error));
                    }
                }
            });

        return id;
    }

    @NonNull
    @Override
    public Message.Id uploadFileToServer(@NonNull File file,
                                         @NonNull String fileName,
                                         @NonNull String mimeType,
                                         @Nullable final UploadFileToServerCallback uploadFileToServerCallback) {
        file.getClass(); // NPE
        fileName.getClass(); // NPE
        mimeType.getClass(); // NPE

        accessChecker.checkAccess();

        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id id = StringId.generateForMessage();
        if (!patternMatches(fileName)) {
            if (uploadFileToServerCallback != null) {
                uploadFileToServerCallback.onFailure(id, (new WebimErrorImpl<>(
                        SendFileCallback.SendFileError.FILE_NAME_INCORRECT,
                        WebimInternalError.FILE_NAME_INCORRECT)));
            }
            return id;
        }
        actions.sendFile(
            RequestBody.create(MediaType.parse(mimeType), file),
            fileName,
            id.toString(),
            new SendOrDeleteMessageInternalCallback() {
                @Override
                public void onSuccess(String response) {
                    if (uploadFileToServerCallback != null) {
                        UploadedFile uploadedFile = InternalUtils.getUploadedFile(response);
                        uploadFileToServerCallback.onSuccess(id, uploadedFile);
                    }
                }

                @Override
                public void onFailure(String error) {
                    if (uploadFileToServerCallback != null) {
                        uploadFileToServerCallback.onFailure(id, (new WebimErrorImpl<>(getFileError(error), error)));
                    }
                }
            });
        return id;
    }

    @Override
    public void deleteUploadedFile(@NonNull String fileGuid,
                                   @Nullable final DeleteUploadedFileCallback deleteUploadedFileCallback) {
        accessChecker.checkAccess();
        actions.deleteUploadedFile(
                fileGuid,
                new SendOrDeleteMessageInternalCallback() {
                    @Override
                    public void onSuccess(String response) {
                        if (deleteUploadedFileCallback != null) {
                            deleteUploadedFileCallback.onSuccess();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        if (deleteUploadedFileCallback != null) {
                            deleteUploadedFileCallback.onFailure(new WebimErrorImpl<>(getDeleteUploadedFileError(error), error));
                        }
                    }
                }
        );
    }

    private Boolean patternMatches(String fileName) {
        return Pattern.compile("^[()_.а-яА-ЯёЁa-zA-Z0-9\\+\\s\\-]+$").matcher(fileName).matches();
    }

    private SendFileCallback.SendFileError getFileError(String error) {
        switch (error) {
            case WebimInternalError.CONNECTION_TIMEOUT:
                return SendFileCallback.SendFileError.CONNECTION_TIMEOUT;
            case WebimInternalError.FILE_TYPE_NOT_ALLOWED:
                return SendFileCallback.SendFileError.FILE_TYPE_NOT_ALLOWED;
            case WebimInternalError.FILE_SIZE_EXCEEDED:
                return SendFileCallback.SendFileError.FILE_SIZE_EXCEEDED;
            case WebimInternalError.UPLOADED_FILE_NOT_FOUND:
                return SendFileCallback.SendFileError.UPLOADED_FILE_NOT_FOUND;
            case WebimInternalError.FILE_NOT_FOUND:
                return SendFileCallback.SendFileError.FILE_NOT_FOUND;
            case WebimInternalError.UNAUTHORIZED:
                return SendFileCallback.SendFileError.UNAUTHORIZED;
            case WebimInternalError.FILE_SIZE_TOO_SMALL:
                return SendFileCallback.SendFileError.FILE_SIZE_TOO_SMALL;
            case WebimInternalError.MAX_FILES_COUNT_PER_CHAT_EXCEEDED:
                return SendFileCallback.SendFileError.MAX_FILES_COUNT_PER_CHAT_EXCEEDED;
            case WebimInternalError.MALICIOUS_FILE_DETECTED:
                return SendFileCallback.SendFileError.MALICIOUS_FILE_DETECTED;
            default:
                return SendFileCallback.SendFileError.UNKNOWN;
        }
    }

    private DeleteUploadedFileCallback.DeleteUploadedFileError getDeleteUploadedFileError(String error) {
        switch (error) {
            case WebimInternalError.FILE_NOT_FOUND:
                return DeleteUploadedFileCallback.DeleteUploadedFileError.FILE_NOT_FOUND;
            case WebimInternalError.FILE_HAS_BEEN_SENT:
                return DeleteUploadedFileCallback.DeleteUploadedFileError.FILE_HAS_BEEN_SENT;
            default:
                return DeleteUploadedFileCallback.DeleteUploadedFileError.UNKNOWN;
        }
    }

    @Override
    public void sendSticker(int stickerId, @Nullable SendStickerCallback sendStickerCallback) {
        accessChecker.checkAccess();

        sendStickerInternally(stickerId, null, sendStickerCallback);
    }

    private void sendStickerInternally(
        int stickerId,
        @Nullable Message.Id oldClientSideId,
        @Nullable SendStickerCallback sendStickerCallback
    ) {
        final Message.Id messageId = oldClientSideId == null ? StringId.generateForMessage() : oldClientSideId;
        messageHolder.onSendingMessage(sendingMessageFactory.createSticker(messageId, stickerId), oldClientSideId != null);
        actions.sendSticker(stickerId, messageId.toString(), sendStickerCallback);
    }

    @Override
    public void getLocationConfig(@NonNull String location, @NonNull LocationConfigCallback callback) {
        accessChecker.checkAccess();

        if (locationSettingsItem != null) {
            callback.onSuccess(locationSettingsItem);
            return;
        }

        actions.getAccountConfig(location, response -> {
            locationSettingsItem = response.getLocationSettings();
            if (locationSettingsItem == null) {
                callback.onFailure();
                return;
            }
            callback.onSuccess(locationSettingsItem);
        });
    }

    @Override
    public void sendGeolocation(float latitude, float longitude, @Nullable GeolocationCallback callback) {
        accessChecker.checkAccess();

        actions.sendGeolocation(latitude, longitude, callback);
    }

    @NonNull
    @Override
    public MessageTracker newMessageTracker(@NonNull MessageListener listener) {
        listener.getClass(); // NPE

        accessChecker.checkAccess();

        return messageHolder.newMessageTracker(listener);
    }

    @Override
    public void
    setVisitSessionStateListener(@NonNull VisitSessionStateListener visitSessionStateListener) {
        addVisitSessionStateListener(visitSessionStateListener);
    }

    @Override
    public void addVisitSessionStateListener(@NonNull VisitSessionStateListener visitSessionStateListener) {
        visitSessionStateListeners.add(visitSessionStateListener);
    }

    @Override
    public void removeVisitSessionStateListener(@NonNull VisitSessionStateListener visitSessionStateListener) {
        visitSessionStateListeners.remove(visitSessionStateListener);
    }

    @Override
    public void setCurrentOperatorChangeListener(@NonNull CurrentOperatorChangeListener listener) {
        addCurrentOperatorChangeListener(listener);
    }

    @Override
    public void addCurrentOperatorChangeListener(@NonNull CurrentOperatorChangeListener listener) {
        listener.getClass(); // NPE
        currentOperatorListeners.add(listener);
    }

    @Override
    public void removeCurrentOperatorChangeListener(@NonNull CurrentOperatorChangeListener listener) {
        listener.getClass(); // NPE
        currentOperatorListeners.remove(listener);
    }

    @Override
    public void setChatStateListener(@NonNull ChatStateListener stateListener) {
        addChatStateListener(stateListener);
    }

    @Override
    public void addChatStateListener(@NonNull ChatStateListener stateListener) {
        stateListener.getClass(); // NPE
        stateListeners.add(stateListener);
    }

    @Override
    public void removeChatStateListener(@NonNull ChatStateListener stateListener) {
        stateListeners.remove(stateListener);
    }

    @Override
    public void setOperatorTypingListener(@NonNull OperatorTypingListener operatorTypingListener) {
        addOperatorTypingListener(operatorTypingListener);
    }

    @Override
    public void addOperatorTypingListener(@NonNull OperatorTypingListener operatorTypingListener) {
        operatorTypingListener.getClass(); // NPE
        operatorTypingListeners.add(operatorTypingListener);
    }

    @Override
    public void removeOperatorTypingListener(OperatorTypingListener operatorTypingListener) {
        operatorTypingListeners.remove(operatorTypingListener);
    }

    @Override
    public void setDepartmentListChangeListener(@NonNull DepartmentListChangeListener departmentListChangeListener) {
        addDepartmentListChangeListener(departmentListChangeListener);
    }

    @Override
    public void addDepartmentListChangeListener(@NonNull DepartmentListChangeListener departmentListChangeListener) {
        departmentListChangeListeners.add(departmentListChangeListener);
    }

    @Override
    public void removeDepartmentListChangeListener(@NonNull DepartmentListChangeListener departmentListChangeListener) {
        departmentListChangeListeners.add(departmentListChangeListener);
    }

    @Override
    public void setUnreadByOperatorTimestampChangeListener(
            @Nullable UnreadByOperatorTimestampChangeListener
                    unreadByOperatorTimestampChangeListener
    ) {
        this.unreadByOperatorTimestampChangeListener = unreadByOperatorTimestampChangeListener;
    }

    @Override
    public void setUnreadByVisitorTimestampChangeListener(
            @Nullable UnreadByVisitorTimestampChangeListener unreadByVisitorTimestampChangeListener
    ) {
        this.unreadByVisitorTimestampChangeListener = unreadByVisitorTimestampChangeListener;
    }

    @Override
    public void setUnreadByVisitorMessageCountChangeListener(
            @Nullable UnreadByVisitorMessageCountChangeListener listener) {
        this.unreadByVisitorMessageCountChangeListener = listener;
    }

    @Override
    public void clearChatHistory() {
        accessChecker.checkAccess();

        actions.clearChatHistory(response -> {
            messageHolder.clearHistory();
        });
    }

    @Override
    public void autocomplete(String text, AutocompleteCallback callback) {
        accessChecker.checkAccess();

        if (accountConfigItem != null) {
            autocompleteInternal(text, callback);
            return;
        }

        actions.getAccountConfig(location, response -> {
            onServerConfigsUpdated(response.getAccountConfig(), response.getLocationSettings());
            autocompleteInternal(text, callback);
        });
    }

    private void autocompleteInternal(String text, AutocompleteCallback callback) {
        if (accountConfigItem == null) {
            callback.onFailure(new WebimErrorImpl<>(AutocompleteCallback.AutocompleteError.UNKNOWN, null));
        } else {
            String autocompleteUrl = accountConfigItem.getHintsEndpoint();
            if (autocompleteUrl == null) {
                callback.onFailure(new WebimErrorImpl<>(AutocompleteCallback.AutocompleteError.HINTS_API_INVALID, null));
                return;
            }
            actions.autocomplete(autocompleteUrl, new AutocompleteRequest(text), callback);
        }
    }

    @Override
    public void sendDialogToEmailAddress(@NonNull String email,
                                         @NonNull final SendDialogToEmailAddressCallback sendDialogToEmailAddressCallback) {
        accessChecker.checkAccess();

        actions.sendChatToEmailAddress(email, sendDialogToEmailAddressCallback);
    }

    @Override
    public void addRateOperatorListener(@NonNull RateOperatorListener listener) {
        rateOperatorListeners.add(listener);
    }

    @Override
    public void setGreetingMessageListener(@NonNull GreetingMessageListener listener) {
        greetingMessageListener = listener;
    }

    @Override
    public void sendSurveyAnswer(@NonNull final String surveyAnswer,
                                 @Nullable final SurveyAnswerCallback callback) {
        accessChecker.checkAccess();

        if (surveyController == null) {
            throw new IllegalStateException("SurveyController has not been initialized. " +
                    "Call method setSurveyListener(SurveyListener surveyListener)");
        }

        Survey survey = surveyController.getSurvey();
        if (survey == null) {
            if (callback != null) {
                callback.onFailure(new WebimErrorImpl<>(
                    SurveyAnswerCallback.SurveyAnswerError.NO_CURRENT_SURVEY,
                    null
                ));
            }
            return;
        }

        int formId = surveyController.getCurrentFormId();
        int questionId = surveyController.getCurrentQuestionPointer();
        String surveyId = survey.getId();
        actions.sendQuestionAnswer(
            surveyId,
            formId,
            questionId,
            surveyAnswer,
            new SurveyQuestionCallback() {
                @Override
                public void onSuccess() {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                    surveyController.nextQuestion();
                }

                @Override
                public void onFailure(String error) {
                    if (callback != null) {
                        SurveyAnswerCallback.SurveyAnswerError surveyAnswerError;
                        switch (error) {
                            case WebimInternalError.SURVEY_DISABLED:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.SURVEY_DISABLED;
                                break;
                            case WebimInternalError.NO_CURRENT_SURVEY:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.NO_CURRENT_SURVEY;
                                break;
                            case WebimInternalError.INCORRECT_SURVEY_ID:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.INCORRECT_SURVEY_ID;
                                break;
                            case WebimInternalError.INCORRECT_STARS_VALUE:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.INCORRECT_STARS_VALUE;
                                break;
                            case WebimInternalError.INCORRECT_RADIO_VALUE:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.INCORRECT_RADIO_VALUE;
                                break;
                            case WebimInternalError.MAX_COMMENT_LENGTH_EXCEEDED:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.MAX_COMMENT_LENGTH_EXCEEDED;
                                break;
                            case WebimInternalError.QUESTION_NOT_FOUND:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.QUESTION_NOT_FOUND;
                                break;
                            default:
                                surveyAnswerError = SurveyAnswerCallback.SurveyAnswerError.UNKNOWN;
                        }

                        callback.onFailure(new WebimErrorImpl<>(surveyAnswerError, error));
                    }
                }
            }
        );
    }

    void setOnlineStatus(String onlineStatus) {
        if (this.onlineStatus == null || !this.onlineStatus.equals(onlineStatus)) {
            MessageStream.OnlineStatus oldStatus
                = toPublicOnlineStatus(getOnlineStatus());
            this.onlineStatus = onlineStatus;
            if (onlineStatusChangeListener != null) {
                onlineStatusChangeListener.onOnlineStatusChanged(oldStatus, toPublicOnlineStatus(getOnlineStatus()));
            }
        }
    }

    private OnlineStatusItem getOnlineStatus() {
        return OnlineStatusItem.getType(onlineStatus);
    }

    @Override
    public void closeSurvey(@Nullable final SurveyCloseCallback callback) {
        accessChecker.checkAccess();

        if (surveyController != null) {
            actions.closeSurvey(
                surveyController.getSurvey().getId(),
                new SurveyFinishCallback() {
                    @Override
                    public void onSuccess() {
                        if (callback != null) {
                            callback.onSuccess();
                            surveyController.deleteSurvey();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        if (callback != null) {
                            MessageStream.SurveyCloseCallback.SurveyCloseError surveyCloseError;
                            switch (error) {
                                case WebimInternalError.SURVEY_DISABLED:
                                    surveyCloseError = MessageStream.SurveyCloseCallback.SurveyCloseError.SURVEY_DISABLED;
                                    break;
                                case WebimInternalError.INCORRECT_SURVEY_ID:
                                    surveyCloseError = MessageStream.SurveyCloseCallback.SurveyCloseError.INCORRECT_SURVEY_ID;
                                    break;
                                case WebimInternalError.NO_CURRENT_SURVEY:
                                    surveyCloseError = MessageStream.SurveyCloseCallback.SurveyCloseError.NO_CURRENT_SURVEY;
                                    break;
                                default:
                                    surveyCloseError = MessageStream.SurveyCloseCallback.SurveyCloseError.UNKNOWN;
                            }
                            callback.onFailure(new WebimErrorImpl<>(surveyCloseError, error));
                        }
                    }
                }
            );
        }
    }

    @Override
    public void setSurveyListener(@NonNull SurveyListener surveyListener) {
        this.surveyController = new SurveyController(surveyListener);
    }

    void setUnreadByOperatorTimestamp(long unreadByOperatorTimestamp) {
        long previousValue = this.unreadByOperatorTimestamp;

        this.unreadByOperatorTimestamp = unreadByOperatorTimestamp;

        if ((previousValue != unreadByOperatorTimestamp)
                && (unreadByOperatorTimestampChangeListener != null)) {
            unreadByOperatorTimestampChangeListener
                    .onUnreadByOperatorTimestampChanged(this.getUnreadByOperatorTimestamp());
        }
    }

    void setUnreadByVisitorTimestamp(long unreadByVisitorTimestamp) {
        long previousValue = this.unreadByVisitorTimestamp;

        this.unreadByVisitorTimestamp = unreadByVisitorTimestamp;

        if ((previousValue != unreadByVisitorTimestamp)
                && (unreadByVisitorTimestampChangeListener != null)) {
            unreadByVisitorTimestampChangeListener
                    .onUnreadByVisitorTimestampChanged(this.getUnreadByVisitorTimestamp());
        }
    }

    void setUnreadByVisitorMessageCount(int unreadByVisitorMessageCount) {
        long previousValue = this.unreadByVisitorMessageCount;

        this.unreadByVisitorMessageCount = unreadByVisitorMessageCount;

        if ((previousValue != unreadByVisitorMessageCount)
                && (unreadByVisitorMessageCountChangeListener != null)) {
            unreadByVisitorMessageCountChangeListener
                    .onUnreadByVisitorMessageCountChanged(this.getUnreadByVisitorMessageCount());
        }
    }

    void onChatUpdated(@Nullable ChatItem currentChat) {
        ChatItem oldChat = chat;
        chat = currentChat;
        if (!InternalUtils.equals(chat, oldChat)) {
            if (chat == null) {
                messageHolder.onChatReceive(oldChat, null, Collections.emptyList());
            } else {
                messageHolder.onChatReceive(oldChat, chat, currentChatMessageMapper.mapAll(chat.getMessages()));
            }
        } else {
            messageHolder.onChatReceive(
                oldChat,
                chat,
                chat != null ? currentChatMessageMapper.mapAll(chat.getMessages()) : Collections.emptyList());
        }

        onOperatorUpdated(currentChat);
        onChatStateUpdated(currentChat);
        onOperatorTypingUpdated(currentChat);

        setUnreadByOperatorTimestamp((chat != null) ? chat.getUnreadByOperatorTimestamp() : 0);
        setUnreadByVisitorTimestamp((chat != null) ? chat.getUnreadByVisitorTimestamp() : 0);
        setUnreadByVisitorMessageCount((chat != null) ? chat.getUnreadByVisitorMessageCount() : 0);
    }

    void onOperatorUpdated(@Nullable ChatItem currentChat) {
        chat = currentChat;

        Operator newOperator
            = operatorFactory.createOperator((chat == null) ? null : chat.getOperator());
        if (!InternalUtils.equals(newOperator, currentOperator)) {
            Operator oldOperator = currentOperator;
            currentOperator = newOperator;
            for (CurrentOperatorChangeListener currentOperatorListener : currentOperatorListeners) {
                currentOperatorListener.onOperatorChanged(oldOperator, newOperator);
            }
        }
    }

    void onChatStateUpdated(@Nullable ChatItem currentChat) {
        chat = currentChat;

        ChatItem.ItemChatState newState = (chat == null)
            ? ChatItem.ItemChatState.CLOSED
            : chat.getState();
        if (lastChatState != newState) {
            for (ChatStateListener stateListener : stateListeners) {
                stateListener.onStateChange(toPublicState(lastChatState), toPublicState(newState));
            }
            processRateOperatorListeners(newState);
        }
        lastChatState = newState;
    }

    private void processRateOperatorListeners(ChatItem.ItemChatState newState) {
        Operator operator = getCurrentOperator();
        boolean shouldRateOperator = operator != null && getLastOperatorRating(operator.getId()) == 0;
        boolean isStateClosed = newState == ChatItem.ItemChatState.CLOSED_BY_OPERATOR || newState == ChatItem.ItemChatState.CLOSED_BY_VISITOR;

        if (shouldRateOperator &&
            isStateClosed &&
            accountConfigItem != null && accountConfigItem.isRateOperator() &&
            locationSettingsItem != null && locationSettingsItem.getChat().getProposeToRateBeforeClose() == LocationSettingsItem.ToggleValue.ENABLED
        ) {
            for (RateOperatorListener rateOperatorListener : rateOperatorListeners) {
                rateOperatorListener.onRateOperator();
            }
        }
    }

    void onServerConfigsUpdated(AccountConfigItem accountConfigItem, LocationSettingsItem locationSettingsItem) {
        this.accountConfigItem = accountConfigItem;
        this.locationSettingsItem = locationSettingsItem;
    }

    void onOperatorTypingUpdated(@Nullable ChatItem currentChat) {
        chat = currentChat;

        boolean operatorTypingStatus = currentChat != null && currentChat.isOperatorTyping();

        if (lastOperatorTypingStatus != operatorTypingStatus) {
            for (OperatorTypingListener operatorTypingListener : operatorTypingListeners) {
                operatorTypingListener.onOperatorTypingStateChanged(operatorTypingStatus);
            }
        }
        lastOperatorTypingStatus = operatorTypingStatus;
    }

    void onReceivingDepartmentList(List<DepartmentItem> departmentItemList) {
        List<Department> departmentList = new ArrayList<>();
        for (DepartmentItem departmentItem : departmentItemList) {
            URL fullLogoUrl = null;
            try {
                String logoUrlString = departmentItem.getLogoUrlString();
                if (!logoUrlString.equals("null")) {
                    fullLogoUrl = new URL(serverUrlString + departmentItem.getLogoUrlString());
                }
            } catch (Exception ignored) { }

            final Department department = new DepartmentImpl(
                    departmentItem.getKey(),
                    departmentItem.getName(),
                    toPublicDepartmentOnlineStatus(departmentItem.getOnlineStatus()),
                    departmentItem.getOrder(),
                    departmentItem.getLocalizedNames(),
                    fullLogoUrl
            );
            departmentList.add(department);
        }
        this.departmentList = departmentList;

        for (DepartmentListChangeListener departmentListChangeListener : departmentListChangeListeners) {
            departmentListChangeListener.receivedDepartmentList(departmentList);
        }
    }

    void setInvitationState(VisitSessionStateItem visitSessionState) {
        final VisitSessionStateItem previousVisitSessionState = this.visitSessionState;
        this.visitSessionState = visitSessionState;

        isProcessingChatOpen = false;

        for (VisitSessionStateListener visitSessionStateListener : visitSessionStateListeners) {
            visitSessionStateListener.onStateChange(
                    toPublicVisitSessionState(previousVisitSessionState),
                    toPublicVisitSessionState(visitSessionState)
            );
        }
    }

    void onSurveyReceived(SurveyItem surveyItem) {
        if (surveyController != null) {
            surveyController.setSurvey(surveyFactory.createSurvey(surveyItem));
            surveyController.nextQuestion();
        }
    }

    void onSurveyCancelled() {
        if (surveyController != null) {
            surveyController.cancelSurvey();
        }
    }

    void onFullUpdate(ChatItem currentChat) {
        onChatUpdated(currentChat);
    }

    @NonNull
    @Override
    public LocationSettings getLocationSettings() {
        return locationSettingsHolder.getLocationSettings();
    }

    @Nullable
    @Override
    public AccountConfigItem getAccountConfig() {
        return accountConfigItem;
    }

    void saveLocationSettings(DeltaFullUpdate fullUpdate) {
        boolean hintsEnabled = Boolean.TRUE.equals(fullUpdate.getHintsEnabled());

        LocationSettingsImpl oldLocationSettings = locationSettingsHolder.getLocationSettings();
        LocationSettingsImpl newLocationSettings = new LocationSettingsImpl(hintsEnabled);

        boolean locationSettingsReceived =
                locationSettingsHolder.receiveLocationSettings(newLocationSettings);

        if (locationSettingsReceived
                && (locationSettingsChangeListener != null)) {
            locationSettingsChangeListener.onLocationSettingsChanged(oldLocationSettings,
                    newLocationSettings);
        }
    }

    void handleGreetingMessage(boolean showHelloMessage,
                               boolean chatStartAfterMessage,
                               boolean currentChatEmpty,
                               String helloMessageDescr) {
        if (greetingMessageListener != null
                && chatStartAfterMessage
                && showHelloMessage
                && currentChatEmpty
                && messageHolder.historyMessagesEmpty()) {
            greetingMessageListener.greetingMessage(helloMessageDescr);
        }
    }

    @Override
    public void setLocationSettingsChangeListener(LocationSettingsChangeListener
                                                          locationSettingsChangeListener) {
        this.locationSettingsChangeListener = locationSettingsChangeListener;
    }

    @Override
    public void setOnlineStatusChangeListener(OnlineStatusChangeListener
                                                      onlineStatusChangeListener) {
        this.onlineStatusChangeListener = onlineStatusChangeListener;
    }

    private Department.DepartmentOnlineStatus
    toPublicDepartmentOnlineStatus
            (DepartmentItem.InternalDepartmentOnlineStatus internalDepartmentOnlineStatus) {
        switch (internalDepartmentOnlineStatus) {
            case BUSY_OFFLINE:
                return Department.DepartmentOnlineStatus.BUSY_OFFLINE;
            case BUSY_ONLINE:
                return Department.DepartmentOnlineStatus.BUSY_ONLINE;
            case OFFLINE:
                return Department.DepartmentOnlineStatus.OFFLINE;
            case ONLINE:
                return Department.DepartmentOnlineStatus.ONLINE;
            default:
                return Department.DepartmentOnlineStatus.UNKNOWN;
        }
    }

    private VisitSessionState
    toPublicVisitSessionState(VisitSessionStateItem visitSessionStateItem) {
        switch (visitSessionStateItem) {
            case CHAT:
                return VisitSessionState.CHAT;
            case DEPARTMENT_SELECTION:
                return VisitSessionState.DEPARTMENT_SELECTION;
            case IDLE:
                return VisitSessionState.IDLE;
            case IDLE_AFTER_CHAT:
                return VisitSessionState.IDLE_AFTER_CHAT;
            case OFFLINE_MESSAGE:
                return VisitSessionState.OFFLINE_MESSAGE;
            default:
                return VisitSessionState.UNKNOWN;
        }
    }

    private Message.Id sendMessageInternally(
        String message,
        String data,
        boolean isHintQuestion,
        Message.Id oldClientSideId,
        final DataMessageCallback dataMessageCallback
    ) {
        message.getClass(); // NPE

        accessChecker.checkAccess();

        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id messageId = oldClientSideId == null ? StringId.generateForMessage() : oldClientSideId;
        MessageSending sendingMessage = sendingMessageFactory.createText(messageId, message);
        actions.sendMessage(
            message,
            messageId.toString(),
            data,
            isHintQuestion,
            new SendOrDeleteMessageInternalCallback() {
                @Override
                public void onSuccess(String response) {
                    if (dataMessageCallback != null) {
                        dataMessageCallback.onSuccess(messageId);
                    }
                }

                @Override
                public void onFailure(String error) {
                    messageHolder.onMessageSendingFailed(sendingMessage);
                    if (dataMessageCallback != null) {
                        dataMessageCallback.onFailure(
                            messageId,
                            new WebimErrorImpl<>(toPublicDataMessageError(error), error)
                        );
                    }
                }
            }
        );

        messageHolder.onSendingMessage(sendingMessage, oldClientSideId != null);
        return messageId;
    }

    private boolean editMessageInternally(
        final Message message,
        final String text,
        final EditMessageCallback editMessageCallback
    ) {
        message.getClass(); // NPE
        text.getClass(); // NPE

        if (!message.canBeEdited()) {
            return false;
        }

        accessChecker.checkAccess();

        final String oldMessage = messageHolder.onChangingMessage(message.getClientSideId(), text);

        if (oldMessage != null) {
            actions.sendMessage(
                text,
                message.getClientSideId().toString(),
                message.getData(),
                false,
                new SendOrDeleteMessageInternalCallback() {
                    @Override
                    public void onSuccess(String response) {
                        if (editMessageCallback != null) {
                            editMessageCallback.onSuccess(message.getClientSideId(), text);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        messageHolder.onMessageChangingCancelled(message.getClientSideId(), oldMessage);
                        if (editMessageCallback != null) {
                            editMessageCallback.onFailure(
                                message.getClientSideId(),
                                new WebimErrorImpl<>(toPublicEditMessageError(error), error)
                            );
                        }
                    }
                });
            return true;
        }
        return false;
    }

    private boolean deleteMessageInternally(
        final Message message,
        final DeleteMessageCallback deleteMessageCallback
    ) {
        message.getClass(); // NPE

        if (message.getSendStatus() != Message.SendStatus.SENT) {
            // Delete local message
            messageHolder.receiveHistoryUpdate(
                Collections.emptyList(),
                Collections.singleton(message.getClientSideId().toString()),
                null
            );
            return true;
        }

        if (!message.canBeEdited()) {
            return false;
        }

        accessChecker.checkAccess();

        final String oldMessage = messageHolder.onChangingMessage(message.getClientSideId(), null);
        if (oldMessage != null) {
            actions.deleteMessage(
                message.getClientSideId().toString(),
                new SendOrDeleteMessageInternalCallback() {
                    @Override
                    public void onSuccess(String response) {
                        if (deleteMessageCallback != null) {
                            deleteMessageCallback.onSuccess(message.getClientSideId());
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        messageHolder.onMessageChangingCancelled(message.getClientSideId(), oldMessage);
                        if (deleteMessageCallback != null) {
                            deleteMessageCallback.onFailure(
                                message.getClientSideId(),
                                new WebimErrorImpl<>(toPublicDeleteMessageError(error), error)
                            );
                        }
                    }
                }
            );
            return true;
        }
        return false;
    }

    private void rateOperatorInternally(@NonNull String operatorId,
                                        @Nullable String note,
                                        int rating,
                                        @Nullable RateOperatorCallback rateOperatorCallback) {
        operatorId.getClass(); //NPE

        accessChecker.checkAccess();

        if (accountConfigItem != null && !accountConfigItem.isRateOperator()) {
            if (rateOperatorCallback != null) {
                rateOperatorCallback.onFailure(
                    new WebimErrorImpl<>(
                        RateOperatorCallback.RateOperatorError.OPERATOR_RATING_DISABLE_ON_SERVER,
                        "Operator rating is disabled"
                    )
                );
            }
            return;
        }

        actions.rateOperator(operatorId, note, rateToInternal(rating), rateOperatorCallback);
    }

    private DataMessageCallback.DataMessageError toPublicDataMessageError(String dataMessageError) {
        switch (dataMessageError) {
            case WebimInternalError.QUOTED_MESSAGE_CANNOT_BE_REPLIED:
                return DataMessageCallback.DataMessageError.QUOTED_MESSAGE_CANNOT_BE_REPLIED;
            case WebimInternalError.QUOTED_MESSAGE_CORRUPTED_ID:
            case WebimInternalError.QUOTED_MESSAGE_NOT_FOUND:
                return DataMessageCallback.DataMessageError.QUOTED_MESSAGE_WRONG_ID;
            case WebimInternalError.QUOTED_MESSAGE_FROM_ANOTHER_VISITOR:
                return DataMessageCallback.DataMessageError.QUOTED_MESSAGE_FROM_ANOTHER_VISITOR;
            case WebimInternalError.QUOTED_MESSAGE_MULTIPLE_IDS:
                return DataMessageCallback.DataMessageError.QUOTED_MESSAGE_MULTIPLE_IDS;
            case WebimInternalError.QUOTED_MESSAGE_REQUIRED_ARGUMENTS_MISSING:
                return DataMessageCallback.DataMessageError
                        .QUOTED_MESSAGE_REQUIRED_ARGUMENTS_MISSING;
            default:
                return DataMessageCallback.DataMessageError.UNKNOWN;
        }
    }

    private SendKeyboardCallback.SendKeyboardError toPublicSendKeyboardError(String sendKeyboardError) {
        switch (sendKeyboardError) {
            case WebimInternalError.NO_CHAT:
                return SendKeyboardCallback.SendKeyboardError.NO_CHAT;
            case WebimInternalError.BUTTON_ID_NO_SET:
                return SendKeyboardCallback.SendKeyboardError.BUTTON_ID_NO_SET;
            case WebimInternalError.REQUEST_MESSAGE_ID_NOT_SET:
                return SendKeyboardCallback.SendKeyboardError.REQUEST_MESSAGE_ID_NOT_SET;
            case WebimInternalError.CANNOT_CREATE_RESPONSE:
                return SendKeyboardCallback.SendKeyboardError.CANNOT_CREATE_RESPONSE;
            default:
                return SendKeyboardCallback.SendKeyboardError.UNKNOWN;
        }
    }

    private EditMessageCallback.EditMessageError toPublicEditMessageError(String editMessageError) {
        switch (editMessageError) {
            case WebimInternalError.MESSAGE_EMPTY:
                return EditMessageCallback.EditMessageError.MESSAGE_EMPTY;
            case WebimInternalError.NOT_ALLOWED:
                return EditMessageCallback.EditMessageError.NOT_ALLOWED;
            case WebimInternalError.MESSAGE_NOT_OWNED:
                return EditMessageCallback.EditMessageError.MESSAGE_NOT_OWNED;
            case WebimInternalError.MAX_MESSAGE_LENGTH_EXCEEDED:
                return EditMessageCallback.EditMessageError.MAX_LENGTH_EXCEEDED;
            case WebimInternalError.WRONG_MESSAGE_KIND:
                return EditMessageCallback.EditMessageError.WRONG_MESSAGE_KIND;
            default:
                return EditMessageCallback.EditMessageError.UNKNOWN;
        }
    }

    private DeleteMessageCallback.DeleteMessageError toPublicDeleteMessageError(String deleteMessageError) {
        switch (deleteMessageError) {
            case WebimInternalError.NOT_ALLOWED:
                return DeleteMessageCallback.DeleteMessageError.NOT_ALLOWED;
            case WebimInternalError.MESSAGE_NOT_OWNED:
                return DeleteMessageCallback.DeleteMessageError.MESSAGE_NOT_OWNED;
            case WebimInternalError.MESSAGE_NOT_FOUND:
                return DeleteMessageCallback.DeleteMessageError.MESSAGE_NOT_FOUND;
            default:
                return DeleteMessageCallback.DeleteMessageError.UNKNOWN;
        }
    }
}
