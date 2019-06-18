package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Department;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.impl.backend.LocationSettingsImpl;
import com.webimapp.android.sdk.impl.backend.SendKeyboardErrorListener;
import com.webimapp.android.sdk.impl.backend.SendOrDeleteMessageInternalCallback;
import com.webimapp.android.sdk.impl.backend.WebimActions;
import com.webimapp.android.sdk.impl.backend.WebimInternalError;
import com.webimapp.android.sdk.impl.items.ChatItem;
import com.webimapp.android.sdk.impl.items.DepartmentItem;
import com.webimapp.android.sdk.impl.items.MessageItem;
import com.webimapp.android.sdk.impl.items.OnlineStatusItem;
import com.webimapp.android.sdk.impl.items.RatingItem;
import com.webimapp.android.sdk.impl.items.VisitSessionStateItem;
import com.webimapp.android.sdk.impl.items.delta.DeltaFullUpdate;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;

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
    private CurrentOperatorChangeListener currentOperatorListener;
    private List<Department> departmentList;
    private DepartmentListChangeListener departmentListChangeListener;
    private @NonNull VisitSessionStateItem visitSessionState = VisitSessionStateItem.UNKNOWN;
    private boolean isProcessingChatOpen;
    private @NonNull ChatItem.ItemChatState lastChatState = ChatItem.ItemChatState.UNKNOWN;
    private boolean lastOperatorTypingStatus;
    private LocationSettingsChangeListener locationSettingsChangeListener;
    private OnlineStatusChangeListener onlineStatusChangeListener;
    private MessageFactories.OperatorFactory operatorFactory;
    private @Nullable OperatorTypingListener operatorTypingListener;
    private String serverUrlString;
    private @Nullable ChatStateListener stateListener;
    private long unreadByOperatorTimestamp = -1;
    private @Nullable UnreadByOperatorTimestampChangeListener
            unreadByOperatorTimestampChangeListener;
    private int unreadByVisitorMessageCount = -1;
    private @Nullable UnreadByVisitorMessageCountChangeListener
            unreadByVisitorMessageCountChangeListener;
    private long unreadByVisitorTimestamp = -1;
    private @Nullable UnreadByVisitorTimestampChangeListener
            unreadByVisitorTimestampChangeListener;
    private VisitSessionStateListener visitSessionStateListener;

    MessageStreamImpl(
            String serverUrlString,
            MessageFactories.Mapper<MessageImpl> currentChatMessageMapper,
            MessageFactories.SendingFactory sendingMessageFactory,
            MessageFactories.OperatorFactory operatorFactory,
            AccessChecker accessChecker,
            WebimActions actions,
            MessageHolder messageHolder,
            MessageComposingHandler messageComposingHandler,
            LocationSettingsHolder locationSettingsHolder
    ) {
        this.serverUrlString = serverUrlString;
        this.currentChatMessageMapper = currentChatMessageMapper;
        this.sendingMessageFactory = sendingMessageFactory;
        this.operatorFactory = operatorFactory;
        this.accessChecker = accessChecker;
        this.actions = actions;
        this.messageHolder = messageHolder;
        this.messageComposingHandler = messageComposingHandler;
        this.locationSettingsHolder = locationSettingsHolder;
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
    public void startChat() {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(null, null, null);
    }

    @Override
    public void startChatWithDepartmentKey(@Nullable String departmentKey) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(null, null, departmentKey);
    }

    @Override
    public void startChatWithFirstQuestion(@Nullable String firstQuestion) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(firstQuestion, null, null);
    }

    @Override
    public void startChatWithCustomFields(@Nullable String customFields) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(null, customFields, null);
    }

    @Override
    public void startChatWithDepartmentKeyFirstQuestion(@Nullable String departmentKey,
                                                        @Nullable String firstQuestion) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(firstQuestion, null, departmentKey);
    }

    @Override
    public void startChatWithCustomFieldsFirstQuestion(@Nullable String customFields,
                                                       @Nullable String firstQuestion) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(firstQuestion, customFields, null);
    }

    @Override
    public void startChatWithCustomFieldsDepartmentKey(@Nullable String customFields,
                                                       @Nullable String departmentKey) {
        startChatWithFirstQuestionCustomFieldsDepartmentKey(null, customFields, departmentKey);
    }

    @Override
    public void startChatWithFirstQuestionCustomFieldsDepartmentKey(
            @Nullable String firstQuestion,
            @Nullable String customFields,
            @Nullable String departmentKey
    ) {
        accessChecker.checkAccess();

        if (((lastChatState.isClosed())
                || (visitSessionState == VisitSessionStateItem.OFFLINE_MESSAGE))
                && !isProcessingChatOpen) {
            isProcessingChatOpen = true;

            actions.startChat(StringId.generateClientSide(),
                    departmentKey,
                    firstQuestion,
                    customFields);
        }
    }

    @Override
    public void closeChat() {
        accessChecker.checkAccess();

        if (lastChatState != ChatItem.ItemChatState.CLOSED_BY_VISITOR
                && lastChatState != ChatItem.ItemChatState.CLOSED
                && lastChatState != ChatItem.ItemChatState.UNKNOWN) {
            actions.closeChat();
        }
    }

    @Override
    public void setChatRead() {
        accessChecker.checkAccess();

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
                null
        );
    }

    @NonNull
    @Override
    public Message.Id sendMessage(@NonNull String message,
                                  @Nullable String data,
                                  @Nullable DataMessageCallback dataMessageCallback) {
        return sendMessageInternally(message, data, false, dataMessageCallback);
    }

    @NonNull
    @Override
    public Message.Id sendMessage(@NonNull String message, boolean isHintQuestion) {
        return sendMessageInternally(message, null, isHintQuestion, null);
    }

    public void sendKeyboardRequest(@NonNull String requestMessageId,
                                    @NonNull String buttonId,
                                    @Nullable final SendKeyboardCallback sendKeyboardCallback) {
        final Message.Id messageId = StringId.generateForMessage();
        actions.sendKeyboard(
                requestMessageId,
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
                        messageHolder.onMessageSendingCancelled(messageId);
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
    public boolean editMessage(@NonNull Message message,
                               @NonNull String text,
                               @Nullable EditMessageCallback editMessageCallback) {
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
            case INVITATION:
                return ChatState.INVITATION;
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
        operatorId.getClass(); //NPE

        accessChecker.checkAccess();

        actions.rateOperator(
                ((StringId) operatorId).getInternal(),
                rateToInternal(rating),
                rateOperatorCallback);
    }

    @Override
    public void respondSentryCall(String id) {
        accessChecker.checkAccess();

        actions.respondSentryCall(id);
    }

    @NonNull
    @Override
    public Message.Id sendFile(@NonNull File file,
                               @NonNull String name,
                               @NonNull String mimeType,
                               @Nullable final SendFileCallback callback) {
        file.getClass(); // NPE
        name.getClass(); // NPE
        mimeType.getClass(); // NPE

        accessChecker.checkAccess();

        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id id = StringId.generateForMessage();
        messageHolder.onSendingMessage(sendingMessageFactory.createFile(id));
        actions.sendFile(
                RequestBody.create(MediaType.parse(mimeType), file),
                name,
                id.toString(),
                new SendOrDeleteMessageInternalCallback() {
                    @Override
                    public void onSuccess() {
                        if (callback != null) {
                            callback.onSuccess(id);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        messageHolder.onMessageSendingCancelled(id);

                        if (callback != null) {
                            SendFileCallback.SendFileError fileError;
                            switch (error) {
                                case WebimInternalError.FILE_TYPE_NOT_ALLOWED:
                                    fileError = SendFileCallback.SendFileError.FILE_TYPE_NOT_ALLOWED;
                                    break;
                                case WebimInternalError.FILE_SIZE_EXCEEDED:
                                    fileError = SendFileCallback.SendFileError.FILE_SIZE_EXCEEDED;
                                    break;
                                case WebimInternalError.UPLOADED_FILE_NOT_FOUND:
                                    fileError = SendFileCallback.SendFileError.UPLOADED_FILE_NOT_FOUND;
                                    break;
                                case WebimInternalError.FILE_NOT_FOUND:
                                    fileError = SendFileCallback.SendFileError.FILE_NOT_FOUND;
                                    break;
                                default:
                                    fileError = SendFileCallback.SendFileError.UNKNOWN;
                            }

                            callback.onFailure(id, (new WebimErrorImpl<>(fileError, error)));
                        }
                    }
                });

        return id;
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
        this.visitSessionStateListener = visitSessionStateListener;
    }

    @Override
    public void setCurrentOperatorChangeListener(@NonNull CurrentOperatorChangeListener listener) {
        listener.getClass(); // NPE
        this.currentOperatorListener = listener;
    }

    @Override
    public void setChatStateListener(@NonNull ChatStateListener stateListener) {
        stateListener.getClass(); // NPE
        this.stateListener = stateListener;
    }

    @Override
    public void setOperatorTypingListener(@NonNull OperatorTypingListener operatorTypingListener) {
        operatorTypingListener.getClass(); // NPE
        this.operatorTypingListener = operatorTypingListener;
    }

    @Override
    public void setDepartmentListChangeListener
            (@NonNull DepartmentListChangeListener departmentListChangeListener) {
        this.departmentListChangeListener = departmentListChangeListener;
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

    void onChatStateTransition(@Nullable ChatItem currentChat) {
        ChatItem oldChat = chat;
        chat = currentChat;
        if (this.chat != oldChat) {
            if (chat == null) {
                messageHolder.onChatReceive(oldChat, null, Collections.<MessageImpl>emptyList());
            } else {
                List<MessageItem> currentMessages = new ArrayList<>();
                List<MessageItem> oldMessages = (oldChat == null)
                        ? new ArrayList<MessageItem>()
                        : oldChat.getMessages();
                for (MessageItem item : chat.getMessages()) {
                    if (!oldMessages.contains(item)) {
                        currentMessages.add(item);
                    }
                }
                messageHolder.onChatReceive(oldChat, chat,
                        currentChatMessageMapper.mapAll(currentMessages));
            }
        }
        ChatItem.ItemChatState newState = (chat == null)
                ? ChatItem.ItemChatState.CLOSED
                : chat.getState();
        if (stateListener != null && lastChatState != newState) {
            stateListener.onStateChange(toPublicState(lastChatState), toPublicState(newState));
        }
        lastChatState = newState;
        Operator newOperator
                = operatorFactory.createOperator((chat == null) ? null : chat.getOperator());
        if (!InternalUtils.equals(newOperator, currentOperator)) {
            Operator oldOperator = currentOperator;
            currentOperator = newOperator;
            if (currentOperatorListener != null) {
                currentOperatorListener.onOperatorChanged(oldOperator, newOperator);
            }
        }
        boolean operatorTypingStatus = currentChat != null && currentChat.isOperatorTyping();
        if (operatorTypingListener != null && lastOperatorTypingStatus != operatorTypingStatus) {
            operatorTypingListener.onOperatorTypingStateChanged(operatorTypingStatus);
        }
        lastOperatorTypingStatus = operatorTypingStatus;

        setUnreadByOperatorTimestamp((chat != null) ? chat.getUnreadByOperatorTimestamp() : 0);
        setUnreadByVisitorTimestamp((chat != null) ? chat.getUnreadByVisitorTimestamp() : 0);
        setUnreadByVisitorMessageCount((chat != null) ? chat.getUnreadByVisitorMessageCount() : 0);
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

        if (departmentListChangeListener != null) {
            departmentListChangeListener.receivedDepartmentList(departmentList);
        }
    }

    void setInvitationState(VisitSessionStateItem visitSessionState) {
        final VisitSessionStateItem previousVisitSessionState = this.visitSessionState;
        this.visitSessionState = visitSessionState;

        isProcessingChatOpen = false;

        if (visitSessionStateListener != null) {
            visitSessionStateListener.onStateChange(
                    toPublicVisitSessionState(previousVisitSessionState),
                    toPublicVisitSessionState(visitSessionState)
            );
        }
    }

    void onFullUpdate(ChatItem currentChat) {
        onChatStateTransition(currentChat);
    }

    @NonNull
    @Override
    public LocationSettings getLocationSettings() {
        return locationSettingsHolder.getLocationSettings();
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

    OnlineStatusChangeListener getOnlineStatusChangeListener() {
        return this.onlineStatusChangeListener;
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

    private Message.Id sendMessageInternally(String message,
                                             String data,
                                             boolean isHintQuestion,
                                             final DataMessageCallback dataMessageCallback) {
        message.getClass(); // NPE

        accessChecker.checkAccess();

        startChatWithDepartmentKeyFirstQuestion(null, null);

        final Message.Id messageId = StringId.generateForMessage();
        actions.sendMessage(
                message,
                messageId.toString(),
                data,
                isHintQuestion,
                new SendOrDeleteMessageInternalCallback() {
                    @Override
                    public void onSuccess() {
                        if (dataMessageCallback != null) {
                            dataMessageCallback.onSuccess(messageId);
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        messageHolder.onMessageSendingCancelled(messageId);
                        if (dataMessageCallback != null) {
                            dataMessageCallback.onFailure(
                                    messageId,
                                    new WebimErrorImpl<>(toPublicDataMessageError(error), error)
                            );
                        }
                    }
                });
        messageHolder.onSendingMessage(sendingMessageFactory.createText(messageId, message));

        return messageId;
    }

    private boolean editMessageInternally(final Message message,
                                       final String text,
                                       final EditMessageCallback editMessageCallback) {
        message.getClass(); // NPE
        text.getClass(); // NPE

        if (!message.canBeEdited()) {
            return false;
        }

        accessChecker.checkAccess();

        final String oldMessage = messageHolder.onChangingMessage(message.getId(), text);

        if (oldMessage != null) {
            actions.sendMessage(
                    text,
                    message.getId().toString(),
                    message.getData(),
                    false,
                    new SendOrDeleteMessageInternalCallback() {
                        @Override
                        public void onSuccess() {
                            if (editMessageCallback != null) {
                                editMessageCallback.onSuccess(message.getId(), text);
                            }
                        }

                        @Override
                        public void onFailure(String error) {
                            messageHolder.onMessageChangingCancelled(message.getId(), oldMessage);
                            if (editMessageCallback != null) {
                                editMessageCallback.onFailure(
                                        message.getId(),
                                        new WebimErrorImpl<>(toPublicEditMessageError(error), error)
                                );
                            }
                        }
                    });
            return true;
        }
        return false;
    }

    private boolean deleteMessageInternally(final Message message,
                                         final DeleteMessageCallback deleteMessageCallback) {
        message.getClass(); // NPE

        if (!message.canBeEdited()) {
            return false;
        }

        accessChecker.checkAccess();

        final String oldMessage = messageHolder.onChangingMessage(message.getId(), null);
        if (oldMessage != null) {
            actions.deleteMessage(
                    message.getId().toString(),
                    new SendOrDeleteMessageInternalCallback() {
                        @Override
                        public void onSuccess() {
                            if (deleteMessageCallback != null) {
                                deleteMessageCallback.onSuccess(message.getId());
                            }
                        }

                        @Override
                        public void onFailure(String error) {
                            messageHolder.onMessageChangingCancelled(message.getId(), oldMessage);
                            if (deleteMessageCallback != null) {
                                deleteMessageCallback.onFailure(
                                        message.getId(),
                                        new WebimErrorImpl<>(toPublicDeleteMessageError(error), error)
                                );
                            }
                        }
                    });
            return true;
        }
        return false;
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
            case WebimInternalError.CAN_NOT_CREATE_RESPONSE:
                return SendKeyboardCallback.SendKeyboardError.CAN_NOT_CREATE_RESPONSE;
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