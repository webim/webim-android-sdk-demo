package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.impl.backend.WebimInternalLog;
import com.webimapp.android.sdk.impl.items.ChatItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableList;

public class MessageHolderImpl implements MessageHolder {
    private final AccessChecker accessChecker;
    private final RemoteHistoryProvider historyProvider;

    private final HistoryStorage historyStorage;
    private final List<MessageImpl> currentChatMessages = new ArrayList<>();
    private final List<MessageSending> sendingMessages = new ArrayList<>();
    private boolean isReachedEndOfLocalHistory = false;
    private boolean isReachedEndOfRemoteHistory;
    private int lastChatIndex = 0;
    @Nullable
    private MessageTrackerImpl messageTracker;

    public MessageHolderImpl(AccessChecker accessChecker,
                             RemoteHistoryProvider historyProvider,
                             HistoryStorage historyStorage,
                             boolean isReachedEndOfRemoteHistory) {
        this.accessChecker = accessChecker;
        this.historyProvider = historyProvider;
        this.historyStorage = historyStorage;
        this.isReachedEndOfRemoteHistory = isReachedEndOfRemoteHistory;
    }

    private void checkAccess() {
        accessChecker.checkAccess();
    }

    @Override
    public void receiveHistoryUpdate(List<? extends MessageImpl> messages,
                                     Set<String> deleted,
                                     final Runnable callback) {
        historyStorage.receiveHistoryUpdate(messages, deleted,
                new HistoryStorage.UpdateHistoryCallback() {
                    @Override
                    public void onHistoryChanged(@NonNull MessageImpl message) {
                        if (messageTracker != null) {
                            messageTracker.onHistoryChanged(message);
                        }
                    }

                    @Override
                    public void onHistoryAdded(@Nullable HistoryId before, @NonNull MessageImpl message) {
                        if (!tryMergeWithLastChat(message) && (messageTracker != null)) {
                            messageTracker.onHistoryAdded(before, message);
                        }
                    }

                    private boolean tryMergeWithLastChat(@NonNull MessageImpl historyMessage) {
                        for (int i = 0; i < currentChatMessages.size(); i++) {
                            MessageImpl chatMessage = currentChatMessages.get(i);
                            if (chatMessage.getId().equals(historyMessage.getId())) {
                                if (i < lastChatIndex) {
                                    MessageImpl replacementMessage
                                            = chatMessage.transferToHistory(historyMessage);
                                    currentChatMessages.remove(i);
                                    lastChatIndex--;

                                    if (messageTracker != null) {
                                        messageTracker.idToHistoryMessageMap.put(
                                                historyMessage.getHistoryId().getDbId(),
                                                replacementMessage
                                        );

                                        if (replacementMessage != chatMessage) {
                                            messageTracker.messageListener.messageChanged(
                                                    chatMessage,
                                                    replacementMessage
                                            );
                                        }
                                    }
                                } else {
                                    chatMessage.setSecondaryHistory(historyMessage);
                                    if (messageTracker != null) {
                                        messageTracker.idToHistoryMessageMap.put(
                                                historyMessage.getHistoryId().getDbId(),
                                                historyMessage
                                        );
                                    }
                                }

                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public void onHistoryDeleted(String id) {
                        if (messageTracker != null) {
                            messageTracker.onHistoryDeleted(id);
                        }
                    }

                    @Override
                    public void endOfBatch() {
                        if (messageTracker != null) {
                            messageTracker.onHistoryEndOfBatch();
                        }

                        callback.run();
                    }
                });
    }

    public void setReachedEndOfRemoteHistory(boolean reachedEndOfRemoteHistory) {
        isReachedEndOfRemoteHistory = reachedEndOfRemoteHistory;
        historyStorage.setReachedEndOfRemoteHistory(reachedEndOfRemoteHistory);
    }

    @Override
    public MessageTracker newMessageTracker(@NonNull MessageListener messageListener) {
        if (messageTracker != null) {
            messageTracker.destroy();
        }

        return messageTracker = new MessageTrackerImpl(messageListener);
    }

    private void getLatestMessages(int limit, @NonNull MessageTracker.GetMessagesCallback callback) {
        if (currentChatMessages.size() != 0) {
            respondMessages(callback, currentChatMessages, limit);
        } else {
            historyStorage.getLatest(limit, callback);
        }
    }

    private void getMessages(@NonNull MessageImpl before,
                             int limit,
                             @NonNull final MessageTracker.GetMessagesCallback callback) {
        if (before.getSource().isCurrentChat()) {
            if (currentChatMessages.size() == 0) {
                throw new RuntimeException("Requested history related to " +
                        "@CurrentChat AbstractMessage when current chat is empty");
            }
            @CurrentChat MessageImpl first = currentChatMessages.get(0);
            if (before == first) {
                if (!first.hasHistoryComponent()) {
                    historyStorage.getLatest(limit, callback);
                } else {
                    getMessagesFromHistory(first.getHistoryId(), limit, callback);
                }
            } else {
                getMessagesFromCurrentChat(before, limit, callback);
            }
        } else {
            getMessagesFromHistory(before.getHistoryId(), limit, callback);
        }
    }

    private void getMessagesFromCurrentChat(@NonNull @CurrentChat MessageImpl before,
                                            int limit,
                                            @NonNull final MessageTracker.GetMessagesCallback
                                                    callback) {
        before.getSource().assertCurrentChat();
        int ind = currentChatMessages.indexOf(before);
        if (ind <= 0) {
            throw new RuntimeException("Impossible");
        }
        respondMessages(callback, currentChatMessages, ind, limit);
    }

    private void getMessagesFromHistory(@NonNull final HistoryId before,
                                        final int limit,
                                        @NonNull final MessageTracker.GetMessagesCallback
                                                callback) {
        if (!isReachedEndOfLocalHistory) {
            historyStorage.getBefore(before, limit, new MessageTracker.GetMessagesCallback() {
                @Override
                public void receive(@NonNull final List<? extends Message> messages) {
                    if (messages.isEmpty()) {
                        isReachedEndOfLocalHistory = true;
                        getMessagesFromHistory(before, limit, callback);
                    } else if (messages.size() < limit && !isReachedEndOfRemoteHistory) {
                        requestHistoryBefore(((MessageImpl) messages.get(0)).getHistoryId(),
                                limit - messages.size(),
                                new MessageTracker.GetMessagesCallback() {
                                    @Override
                                    public void receive(@NonNull List<? extends Message> rest) {
                                        List<Message> result = new ArrayList<>(messages);
                                        result.addAll(rest);
                                        callback.receive(unmodifiableList(result));
                                    }
                                });
                    } else {
                        callback.receive(messages);
                    }
                }
            });
        } else if (isReachedEndOfRemoteHistory) {
            callback.receive(Collections.<Message>emptyList());
        } else {
            requestHistoryBefore(before, limit, callback);
        }
    }

    private void requestHistoryBefore(@NonNull HistoryId before,
                                      final int limit,
                                      @NonNull final MessageTracker.GetMessagesCallback callback) {
        historyProvider.requestHistoryBefore(before.getTimeMicros(), new HistoryBeforeCallback() {
            @Override
            public void onSuccess(List<? extends MessageImpl> messages, boolean hasMore) {
                if (messages.isEmpty()) {
                    isReachedEndOfRemoteHistory = true;
                } else {
                    isReachedEndOfLocalHistory = false;
                    historyStorage.receiveHistoryBefore(messages, hasMore);
                }
                respondMessages(callback, messages, limit);
            }
        });
    }

    private static void respondMessages(MessageTracker.GetMessagesCallback callback,
                                        List<? extends Message> messages,
                                        int limit) {
        callback.receive(messages.size() == 0
                ? Collections.<Message>emptyList()
                : unmodifiableList(
                messages.size() <= limit
                        ? messages
                        : messages.subList(messages.size() - limit, messages.size())));
    }

    private static void respondMessages(MessageTracker.GetMessagesCallback callback,
                                        List<? extends Message> messages,
                                        int offset,
                                        int limit) {
        callback.receive(unmodifiableList(messages.subList(Math.max(0, offset - limit), offset)));
    }

    @Override
    public void onChatReceive(@Nullable ChatItem oldChat,
                              @Nullable ChatItem newChat,
                              @CurrentChat List<? extends MessageImpl> newMessages) {
        if (currentChatMessages.isEmpty()) {
            receiveNewMessages(newMessages);
        } else {
            if (newChat == null) {
                historifyCurrentChat();
            } else if (oldChat == null || !isChatsEquals(oldChat, newChat)) {
                historifyCurrentChat();
                receiveNewMessages(newMessages);
            } else {
                mergeCurrentChatWith(newMessages);
            }
        }
    }

    private void historifyCurrentChat() {
        for (Iterator<MessageImpl> iterator = currentChatMessages.iterator(); iterator.hasNext(); ) {
            MessageImpl chatMessage = iterator.next();
            if (chatMessage.hasHistoryComponent()) {
                chatMessage.invert();
                if (messageTracker != null) {
                    String id = chatMessage.getHistoryId().getDbId();
                    MessageImpl historyMessage = messageTracker.idToHistoryMessageMap.get(id);
                    if ((historyMessage != null)) {
                        historyMessage.setCanBeEdited(false);
                        if (!MessageImpl.isContentEquals(chatMessage, historyMessage)) {
                            messageTracker.messageListener.messageChanged(chatMessage, historyMessage);
                        } else {
                            messageTracker.idToHistoryMessageMap.put(id, chatMessage);
                        }
                    }
                }
                iterator.remove();
            } else {
                if (messageTracker != null && chatMessage.canBeEdited()) {
                    chatMessage.setCanBeEdited(false);
                    messageTracker.messageListener.messageChanged(chatMessage, chatMessage);
                }
            }
        }

        lastChatIndex = currentChatMessages.size();
    }

    private void mergeCurrentChatWith(@CurrentChat List<? extends MessageImpl> newMessages) {
        int oldMessageIndex = this.lastChatIndex;
        boolean isOldMessagesEnded = false;
        for (int newMessageIndex = 0; (newMessageIndex < newMessages.size()); newMessageIndex++) {
            MessageImpl newMessage = newMessages.get(newMessageIndex);
            if (!isOldMessagesEnded) {
                boolean merged = false;
                while (oldMessageIndex < currentChatMessages.size()) {
                    MessageImpl oldMessage = currentChatMessages.get(oldMessageIndex);
                    if (oldMessage.getId().equals(newMessage.getId())) {
                        if (!MessageImpl.isContentEquals(oldMessage, newMessage)) {
                            currentChatMessages.set(oldMessageIndex, newMessage);
                            if (messageTracker != null) {
                                messageTracker.onCurrentChatMessageChanged(
                                        oldMessageIndex,
                                        oldMessage,
                                        newMessage
                                );
                            }
                        }
                        merged = true;
                        oldMessageIndex++;

                        break;
                    } else {
                        currentChatMessages.remove(oldMessageIndex);

                        if (messageTracker != null) {
                            messageTracker.onCurrentChatMessageDeleted(oldMessageIndex, oldMessage);
                        }
                    }
                }
                if (!merged && (oldMessageIndex >= currentChatMessages.size())) {
                    isOldMessagesEnded = true;
                }
            }
            if (isOldMessagesEnded) {
                receiveNewMessage(newMessage);
            }
        }
    }

    @Override
    public void receiveNewMessage(@NonNull @CurrentChat MessageImpl msg) {
        if (messageTracker != null) {
            messageTracker.onNewMessage(msg);
        }
        else {
            currentChatMessages.add(msg);
        }
    }

    private void receiveNewMessages(@NonNull @CurrentChat List<? extends MessageImpl> newMessages) {
        if (!newMessages.isEmpty()) {
            if (messageTracker != null) {
                messageTracker.onNewMessages(newMessages);
            } else {
                currentChatMessages.addAll(newMessages);
            }
        }
    }

    @Override
    public void onMessageChanged(@NonNull @CurrentChat MessageImpl newMessage) {
        for (int i = lastChatIndex; i < currentChatMessages.size(); i++) {
            @CurrentChat MessageImpl oldMessage = currentChatMessages.get(i);
            if (oldMessage.getIdInCurrentChat().equals(newMessage.getIdInCurrentChat())) {
                currentChatMessages.set(i, newMessage);
                if (messageTracker != null) {
                    messageTracker.onCurrentChatMessageChanged(i, oldMessage, newMessage);
                }

                break;
            }
        }
    }

    @Override
    public void onMessageDeleted(@NonNull String idInCurrentChat) {
        for (int i = lastChatIndex; i < currentChatMessages.size(); i++) {
            @CurrentChat MessageImpl oldMessage = currentChatMessages.get(i);
            if (oldMessage.getIdInCurrentChat().equals(idInCurrentChat)) {
                currentChatMessages.remove(i);

                if (messageTracker != null) {
                    messageTracker.onCurrentChatMessageDeleted(i, oldMessage);
                }

                break;
            }
        }
    }

    @Override
    public void onSendingMessage(@NonNull MessageSending message) {
        sendingMessages.add(message);

        if (messageTracker != null) {
            messageTracker.messageListener.messageAdded(null, message);
        }
    }

    @Override
    @Nullable
    public String onChangingMessage(@NonNull Message.Id id, @Nullable String text) {
        if (messageTracker != null) {
            MessageImpl message = null;
            for (MessageImpl curr : currentChatMessages) {
                if (curr.id.equals(id)) {
                    message = curr;
                    break;
                }
            }
            if (message == null) {
                return null;
            }

            MessageImpl newMsg = new MessageImpl(
                    message.serverUrl,
                    message.id,
                    message.operatorId,
                    message.avatarUrl,
                    message.senderName,
                    message.type,
                    text == null ? message.text : text,
                    message.timeMicros,
                    message.attachment,
                    message.getCurrentChatId(),
                    message.getRawText(),
                    message.isHistoryMessage,
                    message.getData(),
                    message.isReadByOperator(),
                    message.canBeEdited(),
                    message.getKeyboard(),
                    message.getKeyboardRequest());
            newMsg.sendStatus = Message.SendStatus.SENDING;
            messageTracker.messageListener.messageChanged(message, newMsg);
            return message.text;
        }
        return null;
    }

    @Override
    public void onMessageSendingCancelled(@NonNull Message.Id id) {
        for (Iterator<MessageSending> it = sendingMessages.iterator(); it.hasNext(); ) {
            MessageSending msg = it.next();
            if (msg.getId().equals(id)) {
                it.remove();
                if (messageTracker != null) {
                    messageTracker.messageListener.messageRemoved(msg);
                }

                break;
            }
        }
    }

    @Override
    public void onMessageChangingCancelled(@NonNull Message.Id id, @NonNull String text) {
        if (messageTracker != null) {
            MessageImpl message = null;
            for (MessageImpl curr : currentChatMessages) {
                if (curr.id.equals(id)) {
                    message = curr;
                    break;
                }
            }
            if (message == null) {
                return;
            }

            MessageImpl newMsg = new MessageImpl(
                    message.serverUrl,
                    message.id,
                    message.operatorId,
                    message.avatarUrl,
                    message.senderName,
                    message.type,
                    text,
                    message.timeMicros,
                    message.attachment,
                    message.getCurrentChatId(),
                    message.getRawText(),
                    message.isHistoryMessage,
                    message.getData(),
                    message.isReadByOperator(),
                    message.canBeEdited(),
                    message.getKeyboard(),
                    message.getKeyboardRequest());
            newMsg.sendStatus = Message.SendStatus.SENT;
            messageTracker.messageListener.messageChanged(message, newMsg);
        }
    }

    @Override
    public void updateReadBeforeTimestamp(Long timestamp) {
        historyStorage.getReadBeforeTimestampListener().onTimestampChanged(timestamp);
    }

    private boolean isChatsEquals(@NonNull ChatItem c1, @NonNull ChatItem c2) {
        return c1.getId() != null && c1.getId().equals(c2.getId()) ||
                c1.getClientSideId() != null && c1.getClientSideId().equals(c2.getClientSideId());
    }

    private class MessageTrackerImpl implements MessageTracker {
        private final MessageListener messageListener;
        private final Map<String, MessageImpl> idToHistoryMessageMap = new HashMap<>();
        private MessageImpl headMessage;
        private boolean isMessagesLoading;
        private boolean isFirstHistoryUpdateReceived;
        private boolean isAllMessageSourcesEnded;
        private boolean isDestroyed;

        private GetMessagesCallback cachedCallback;
        private int cachedLimit;

        private MessageTrackerImpl(MessageListener messageListener) {
            this.messageListener = messageListener;
        }

        @Override
        public void getNextMessages(final int limitOfMessages,
                                    @NonNull final GetMessagesCallback callback) {
            checkAccess();
            if (isDestroyed) {
                throw new IllegalStateException("WebimMessageTracker is destroyed");
            }
            if (isMessagesLoading) {
                throw new IllegalStateException("Messages is loading now; " +
                        "can't load messages in parallel");
            }
            if (limitOfMessages <= 0) {
                throw new IllegalArgumentException("limit must be greater than zero. " +
                        "Given " + limitOfMessages);
            }
            isMessagesLoading = true;
            if (isFirstHistoryUpdateReceived ||
                    (currentChatMessages.size() != 0 && currentChatMessages.get(0) != headMessage)) {
                uncheckedGetNextMessages(limitOfMessages, callback);
            } else {
                cachedCallback = callback;
                cachedLimit = limitOfMessages;

                historyStorage.getLatest(limitOfMessages, new GetMessagesCallback() {
                    @Override
                    public void receive(@NonNull List<? extends Message> messages) {
                        if (cachedCallback != null && !messages.isEmpty()) {
                            final GetMessagesCallback callback = cachedCallback;
                            cachedCallback = null;
                            isFirstHistoryUpdateReceived = true;
                            new GetMessagesCallbackWrapper(limitOfMessages, new GetMessagesCallback() {
                                @Override
                                public void receive(@NonNull List<? extends Message> messages) {
                                    if (!isDestroyed) {
                                        callback.receive(messages);
                                    }
                                }
                            }).receive(messages);
                        }
                    }
                });
            }
        }

        @Override
        public void getLastMessages(final int limitOfMessages, GetMessagesCallback callback) {
            checkAccess();
            if (isDestroyed) {
                throw new IllegalStateException("WebimMessageTracker is destroyed");
            }
            if (isMessagesLoading) {
                throw new IllegalStateException("Messages is loading now; " +
                        "can't load messages in parallel");
            }
            if (limitOfMessages <= 0) {
                throw new IllegalArgumentException("limit must be greater than zero. " +
                        "Given " + limitOfMessages);
            }

            isMessagesLoading = true;
            currentChatMessages.clear();
            cachedCallback = callback;
            cachedLimit = limitOfMessages;
            isReachedEndOfRemoteHistory = false;
            isReachedEndOfLocalHistory = false;
            isAllMessageSourcesEnded = false;

            historyStorage.getLatest(limitOfMessages, new GetMessagesCallback() {
                @Override
                public void receive(@NonNull List<? extends Message> messages) {
                    if (cachedCallback != null
                            && (!messages.isEmpty() || isFirstHistoryUpdateReceived)) {
                        final GetMessagesCallback callback = cachedCallback;
                        cachedCallback = null;
                        isFirstHistoryUpdateReceived = true;
                        new GetMessagesCallbackWrapper(limitOfMessages, new GetMessagesCallback() {
                            @Override
                            public void receive(@NonNull List<? extends Message> messages) {
                                if (!isDestroyed) {
                                    callback.receive(messages);
                                }
                            }
                        }).receive(messages);
                    }
                }
            });
            headMessage = null;
        }

        @Override
        public void getAllMessages(final GetMessagesCallback callback) {
            checkAccess();
            if (isDestroyed) {
                throw new IllegalStateException("WebimMessageTracker is destroyed");
            }

            historyStorage.getFull(new GetMessagesCallback() {
                @Override
                public void receive(@NonNull List<? extends Message> messages) {
                    if (!isDestroyed) {
                        callback.receive(messages);
                    }
                }
            });
        }

        private void uncheckedGetNextMessages(int limit,
                                              @NonNull final GetMessagesCallback callback) {
            GetMessagesCallback callback2 = new GetMessagesCallbackWrapper(limit, callback);
            if (headMessage == null) {
                getLatestMessages(limit, callback2);
            } else {
                getMessages(headMessage, limit, callback2);
            }
        }

        @Override
        public void resetTo(@NonNull Message message) {
            checkAccess();

            if (isDestroyed) {
                throw new IllegalStateException("WebimMessageTracker is destroyed");
            }
            if (isMessagesLoading) {
                throw new IllegalStateException("Messages is loading now; can't reset in parallel");
            }

            MessageImpl unwrappedMessage = (MessageImpl) message;
            if (unwrappedMessage != headMessage) {
                isReachedEndOfLocalHistory = false;
            }
            if (unwrappedMessage.getSource().isHistory()) {
                for (Iterator<MessageImpl> iterator = idToHistoryMessageMap.values().iterator();
                     iterator.hasNext();
                        ) {
                    MessageImpl iteratedMessage = iterator.next();
                    if (iteratedMessage.getTimeMicros() < unwrappedMessage.getTimeMicros()) {
                        iterator.remove();
                    }
                }
            } else {
                idToHistoryMessageMap.clear();
            }

            headMessage = unwrappedMessage;
        }

        @Override
        public void destroy() {
            checkAccess();

            if (!isDestroyed) {
                isDestroyed = true;

                sendingMessages.clear();

                if (messageTracker == this) {
                    messageTracker = null;
                }
            }
        }

        void onNewMessage(final @CurrentChat MessageImpl message) {
            message.getSource().assertCurrentChat();
            addNewOrMergeMessage(message);
            if (headMessage == null && !isAllMessageSourcesEnded) {
                if (cachedCallback != null) { // TODO on endOfBatch only
                    GetMessagesCallback callback = cachedCallback;
                    cachedCallback = null;
                    uncheckedGetNextMessages(cachedLimit, callback);
                }
            }
        }

        void onNewMessages(final @CurrentChat List<? extends MessageImpl> newMessages) {
            if (headMessage != null || isAllMessageSourcesEnded) {
                for (@CurrentChat MessageImpl message : newMessages) {
                    addNewOrMergeMessage(message);
                }
            } else {
                currentChatMessages.addAll(newMessages);
                if (cachedCallback != null) {
                    GetMessagesCallback callback = cachedCallback;
                    cachedCallback = null;
                    uncheckedGetNextMessages(cachedLimit, callback);
                }
            }
        }

        private void addNewOrMergeMessage(final @CurrentChat MessageImpl message) {
            message.getSource().assertCurrentChat();

            boolean callAdded = true;

            if (headMessage == null) {
                headMessage = message;
            } else if (headMessage.getTimeMicros() > message.getTimeMicros()) {
                callAdded = false;

                currentChatMessages.add(message);
            } else {
                // TODO optimize (do no iterate all)
                for (Iterator<MessageImpl> iterator = idToHistoryMessageMap.values().iterator();
                     iterator.hasNext(); ) {
                    MessageImpl historyMessage = iterator.next();
                    if (message.getId().equals(historyMessage.getId())) {
                        MessageImpl replacementMessage =
                                historyMessage.transferToCurrentChat(message);
                        currentChatMessages.add(replacementMessage);

                        callAdded = false;

                        if (replacementMessage != historyMessage) {
                            messageListener.messageChanged(historyMessage, replacementMessage);
                        }

                        iterator.remove();

                        break;
                    }
                }
            }

            if (callAdded) {
                currentChatMessages.add(message);

                MessageSending messageToSend = findMessageSendingMirror(message);
                if (messageToSend != null) {
                    messageListener.messageChanged(messageToSend, message);
                    sendingMessages.remove(messageToSend);
                } else {
                    messageListener.messageAdded(sendingMessages.isEmpty()
                            ? null
                            : sendingMessages.get(0), message);
                }
            }
        }

        @Nullable
        private MessageSending findMessageSendingMirror(MessageImpl msg) {
            for (MessageSending ms : sendingMessages) {
                if (ms.getId().equals(msg.getId())) {
                    return ms;
                }
            }
            return null;
        }

        void onCurrentChatMessageChanged(int index,
                                         @CurrentChat MessageImpl from,
                                         @CurrentChat MessageImpl to) {
            from.getSource().assertCurrentChat();
            to.getSource().assertCurrentChat();
            if (headMessage != null && headMessage.getSource().isHistory()
                    || index >= currentChatMessages.indexOf(headMessage)) {
                if (from == headMessage) {
                    headMessage = to;
                }
                messageListener.messageChanged(from, to);
            }
        }

        void onCurrentChatMessageDeleted(int index, @CurrentChat MessageImpl msg) {
            msg.getSource().assertCurrentChat();
            if (headMessage != null) {
                int headIndex = currentChatMessages.indexOf(headMessage);
                if (headMessage.getSource().isHistory() || index > headIndex) {
                    if (index + 1 == headIndex) {
                        headMessage = currentChatMessages.size() < headIndex
                                ? null
                                : currentChatMessages.get(headIndex);
                    }
                    messageListener.messageRemoved(msg);
                }
            }
        }

        void onHistoryChanged(@NonNull MessageImpl msg) {
            msg.getSource().assertHistory();
            if (headMessage != null
                    && headMessage.getSource().isHistory()
                    && msg.getTimeMicros() >= headMessage.getTimeMicros()) {
                MessageImpl prev = idToHistoryMessageMap.put(msg.getHistoryId().getDbId(), msg);
                if (prev != null) {
                    messageListener.messageChanged(prev, msg);
                } else {
                    WebimInternalLog.getInstance().log(
                            "Unknown message was changed:" + msg.getHistoryId().getDbId(),
                            Webim.SessionBuilder.WebimLogVerbosityLevel.WARNING
                    );
                }
            }
        }

        void onHistoryDeleted(String id) {
            MessageImpl msg = idToHistoryMessageMap.remove(id);
            if (headMessage != null
                    && headMessage.getSource().isHistory()
                    && msg != null && msg.getTimeMicros() >= headMessage.getTimeMicros()) {
                messageListener.messageRemoved(msg);
            }
        }

        void onHistoryAdded(@Nullable HistoryId before, MessageImpl msg) {
            msg.getSource().assertHistory();
            if (headMessage != null && headMessage.getSource().isHistory()) {
                if (before != null) {
                    MessageImpl beforeMsg = idToHistoryMessageMap.get(before.getDbId());
                    if (beforeMsg != null) {
                        idToHistoryMessageMap.put(msg.getHistoryId().getDbId(), msg);
                        messageListener.messageAdded(beforeMsg, msg);
                    }
                } else {
                    idToHistoryMessageMap.put(msg.getHistoryId().getDbId(), msg);
                    messageListener.messageAdded(
                            currentChatMessages.size() != 0
                            ? currentChatMessages.get(0)
                            : null,
                            msg);
                }
            }
        }

        void onHistoryEndOfBatch() {
            if (!isFirstHistoryUpdateReceived) {
                isFirstHistoryUpdateReceived = true;
                if (cachedCallback != null) {
                    GetMessagesCallback callback = cachedCallback;
                    cachedCallback = null;
                    uncheckedGetNextMessages(cachedLimit, callback);
                }
            }
        }

        private class GetMessagesCallbackWrapper implements GetMessagesCallback {
            private int limit;
            private final GetMessagesCallback wrapped;

            private GetMessagesCallbackWrapper(int limit, GetMessagesCallback wrapped) {
                this.limit = limit;
                this.wrapped = wrapped;
            }

            @Override
            public void receive(@NonNull List<? extends Message> messages) {
                List<? extends Message> result;
                if (!messages.isEmpty()) {
                    if (!currentChatMessages.isEmpty()
                            && messages.get(messages.size() - 1).getTime() >= currentChatMessages.get(0).getTime()) {
                        // We received history that overlap current chat messages. Merging
                        List<Message> filtered = new ArrayList<>(messages.size());
                        MessageImpl first = (MessageImpl) messages.get(0);
                        for (Message msg1 : messages) {
                            MessageImpl msg = (MessageImpl) msg1;
                            boolean allow = true;
                            if (msg.getSource().isHistory()) {
                                if (msg.getTime() >= currentChatMessages.get(0).getTime()
                                        && msg.getTime() <= currentChatMessages.get(currentChatMessages.size() - 1).getTime()) {
                                    for (MessageImpl cc : currentChatMessages) {
                                        if (cc.getId().equals(msg.getId())) {
                                            allow = false;
                                            cc.setSecondaryHistory(msg);
                                            break;
                                        }
                                    }
                                }
                            }
                            if (allow) {
                                filtered.add(msg);
                            }
                        }
                        if (filtered.isEmpty()) {
                            getMessages(first, limit, this);
                            return;
                        }
                        result = filtered;
                    } else {
                        result = messages;
                    }

                    for (Message msg : messages) {
                        if (((MessageImpl) msg).getSource().isHistory()) {
                            idToHistoryMessageMap.put(
                                    ((MessageImpl) msg).getHistoryId().getDbId(),
                                    (MessageImpl) msg
                            );
                        }
                    }
                    MessageImpl first = (MessageImpl) result.get(0);
                    if (headMessage == null || compare(first, headMessage) < 0) {
                        headMessage = first;
                    }
                } else {
                    result = messages;
                    isAllMessageSourcesEnded = true;
                }
                isMessagesLoading = false;
                wrapped.receive(unmodifiableList(result));
            }

            private int compare(MessageImpl left, MessageImpl right) {
                return InternalUtils.compare(left.getTimeMicros(), right.getTimeMicros());
            }
        }
    }
}
