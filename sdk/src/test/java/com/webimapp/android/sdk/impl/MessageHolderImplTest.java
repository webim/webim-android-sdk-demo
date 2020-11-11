package com.webimapp.android.sdk.impl;

import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.impl.items.ChatItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(MockitoJUnitRunner.class)
public class MessageHolderImplTest {

    private Runnable EMPTY_RUNNABLE = () -> {};

    private Set<String> EMPTY_SET = Collections.emptySet();

    private List<MessageImpl> EMPTY_MESSAGES_LIST = Collections.emptyList();

    private ArgumentCaptor<List<Message>> newListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private ArgumentCaptor<Message> newMessageCaptor() {
        return ArgumentCaptor.forClass(Message.class);
    }

    static class History implements RemoteHistoryProvider {
        List<MessageImpl> history;

        History(List<MessageImpl> history) {
            this.history = history;
        }

        @Override
        public void requestHistoryBefore(long beforeMessageTs, HistoryBeforeCallback callback) {
            int before = -1;
            for (int i = history.size() - 1; i >= 0; i--) {
                MessageImpl historyMessage = history.get(i);
                if (historyMessage.getTimeMicros() <= beforeMessageTs) {
                    before = i;
                    break;
                }
            }
            int after = Math.max(0, before - 100);
            callback.onSuccess(
                before <= 0
                    ? Collections.emptyList()
                    : history.subList(after, before),
                after != 0
            );
        }
    }

    private int messageIndexCounter = 0;

    private List<MessageImpl> generateHistory(int count) {
        List<MessageImpl> history = new ArrayList<>();
        for (int i = messageIndexCounter; i < messageIndexCounter + count; i++) {
            history.add(new MessageImpl(
                "",
                StringId.forMessage(String.valueOf(i)),
                null,
                StringId.forOperator("op"),
                "",
                "",
                Message.Type.OPERATOR,
                "text",
                i,
                String.valueOf(i),
                null,
                true,
                null,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null
            ));
        }
        messageIndexCounter += count;
        return history;
    }

    private List<MessageImpl> generateCurrentChat(int count) {
        List<MessageImpl> currentChatMessages = new ArrayList<>();
        for (int i = messageIndexCounter; i < messageIndexCounter + count; i++) {
            currentChatMessages.add(new MessageImpl(
                "",
                StringId.forMessage(String.valueOf(i)),
                null,
                StringId.forOperator("op"),
                "",
                "",
                Message.Type.OPERATOR,
                "text",
                i,
                String.valueOf(i),
                null,
                false,
                null,
                false,
                true,
                false,
                false,
                null,
                null,
                null,
                null
            ));
        }
        messageIndexCounter += count;
        return currentChatMessages;
    }

    private List<MessageImpl> generateHistoryFromCurrentChat(List<? extends MessageImpl> input) {
        List<MessageImpl> historyMessages = new ArrayList<>();
        for (MessageImpl currentChatMessage : input) {
            historyMessages.add(
                new MessageImpl(
                    "",
                    currentChatMessage.getId(),
                    currentChatMessage.getSessionId(),
                    currentChatMessage.getOperatorId(),
                    currentChatMessage.getAvatarUrlLastPart(),
                    currentChatMessage.getSenderName(),
                    currentChatMessage.getType(),
                    currentChatMessage.getText(),
                    currentChatMessage.timeMicros,
                    String.valueOf(currentChatMessage.timeMicros),
                    null,
                    true,
                    null,
                    false,
                    true,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null
                )
            );
        }
        return historyMessages;
    }

    private MessageImpl newCurrentChatMessage() {
        int i = messageIndexCounter++;
        return new MessageImpl(
            "",
            StringId.forMessage(String.valueOf(i)),
            null,
            StringId.forOperator("op"),
            "",
            "",
            Message.Type.OPERATOR,
            "text",
            i,
            String.valueOf(i),
            null,
            false,
            null,
            false,
            true,
            false,
            false,
            null,
            null,
            null,
            null
        );
    }

    private MessageImpl newEditedCurrentChatMessage(MessageImpl original) {
        return new MessageImpl(
            "",
            original.getId(),
            original.getSessionId(),
            original.getOperatorId(),
            original.getAvatarUrlLastPart(),
            original.getSenderName(),
            original.getType(),
            original.getText() + "1",
            original.getTimeMicros(),
            original.getIdInCurrentChat(),
            null,
            false,
            null,
            false,
            true,
            false,
            false,
            null,
            null,
            null,
            null
        );
    }

    private MessageImpl newEditedHistoryMessage(MessageImpl original) {
        return new MessageImpl(
            "",
            original.getId(),
            original.getSessionId(),
            original.getOperatorId(),
            original.getAvatarUrlLastPart(),
            original.getSenderName(),
            original.getType(),
            original.getText() + "1",
            original.getTimeMicros(),
            original.getHistoryId().getDbId(),
            null,
            true,
            null,
            false,
            true,
            false,
            false,
            null,
            null,
            null,
            null
        );
    }

    private MessageHolderImpl newMessageHolderWithHistory(RemoteHistoryProvider history) {
        return new MessageHolderImpl(AccessChecker.EMPTY, history, new MemoryHistoryStorage(), false);
    }

    private MessageHolderImpl newMessageHolderWithHistory(RemoteHistoryProvider history, List<MessageImpl> localHistory) {
        return new MessageHolderImpl(AccessChecker.EMPTY, history, new MemoryHistoryStorage(localHistory), false);
    }

    private List<MessageImpl> concat(List<MessageImpl> list1, List<MessageImpl> list2) {
        List<MessageImpl> resultList = new ArrayList<>();
        resultList.addAll(list1);
        resultList.addAll(list2);
        return resultList;
    }

    private void setCurrentChatMessages(MessageHolder messageHolder, List<MessageImpl> messages) {
        try {
            Field currentChatMessages = messageHolder.getClass().getDeclaredField("currentChatMessages");
            currentChatMessages.setAccessible(true);
            currentChatMessages.set(messageHolder, messages);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private List<MessageImpl> cloneCurrentChatMessages(List<MessageImpl> currentChatMessages) {
        try {
            Method method = currentChatMessages.getClass().getDeclaredMethod("clone");
            method.setAccessible(true);
            return (List<MessageImpl>) method.invoke(currentChatMessages);
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private void resetLastChatMessageIndex(MessageHolder messageHolder) {
        try {
            Field lastChatMessageIndex = messageHolder.getClass().getDeclaredField("lastChatMessageIndex");
            lastChatMessageIndex.setAccessible(true);
            lastChatMessageIndex.set(messageHolder, 0);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Test
    public void internalHistoryGeneration() {
        List<MessageImpl> history1 = generateHistory(10);
        generateCurrentChat(10);
        List<MessageImpl> history3 = generateHistory(10);

        List<String> ids1 = history1.stream()
            .map(MessageImpl::getPrimaryId)
            .collect(Collectors.toList());

        List<String> ids3 = history3.stream()
            .map(MessageImpl::getPrimaryId)
            .collect(Collectors.toList());

        assertEquals(ids1, IntStream.rangeClosed(0, 9).boxed().map(String::valueOf).collect(Collectors.toList()));
        assertEquals(ids3, IntStream.rangeClosed(20, 29).boxed().map(String::valueOf).collect(Collectors.toList()));
    }

    @Test
    public void respondCurrentChatMessagesImmediately() {
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        MessageHolder messageHolder = newMessageHolderWithHistory(new History(Collections.<MessageImpl>emptyList()));
        setCurrentChatMessages(messageHolder, currentChatMessages);
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker messageTracker = messageHolder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        messageTracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChatMessages);
    }

    @Test
    public void awaitHistoryResponse() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> history2 = generateHistory(10);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        // request 10 messages
        tracker.getNextMessages(10, callback);
        // no callback called because history has not been received; callback was cached an will be called on history receive
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
        reset(callback);

        // history receive
        holder.receiveHistoryUpdate(history2, EMPTY_SET, EMPTY_RUNNABLE);
        // called previously cached callback
        verify(callback, times(1)).receive(history2);
        reset(callback);

        // request next 10 messages
        tracker.getNextMessages(10, callback);
        // received older history
        verify(callback, times(1)).receive(history1);
    }

    @Test
    public void awaitHistoryResponseWithCurrentChat() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> history2 = generateHistory(10);
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        setCurrentChatMessages(holder, currentChatMessages);
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        tracker.getNextMessages(currentChatMessages.size(), callback);
        verify(callback, times(1)).receive(currentChatMessages);
        reset(callback);

        tracker.getNextMessages(10, callback);
        verify(callback, times(0)).receive(EMPTY_MESSAGES_LIST);
        reset(callback);

        holder.receiveHistoryUpdate(history2, EMPTY_SET, EMPTY_RUNNABLE);
        verify(callback, times(1)).receive(history2);
        reset(callback);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history1);
        reset(callback);
    }

    @Test
    public void insertHistoryBetweenOlderHistoryAndCurrentChat() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> history2 = generateHistory(2);
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        MessageHolder messageHolder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = messageHolder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<List<Message>> listCaptor;
        ArgumentCaptor<Message> messageCaptor;

        messageHolder.receiveHistoryUpdate(history1, EMPTY_SET, EMPTY_RUNNABLE);
        messageHolder.onChatReceive(null, new ChatItem(), currentChatMessages);

        listCaptor = newListCaptor();
        tracker.getNextMessages(10, callback);
        tracker.getNextMessages(10, callback);
        verify(callback, times(2)).receive(listCaptor.capture());
        assertEquals(currentChatMessages, listCaptor.getAllValues().get(0));
        assertEquals(history1, listCaptor.getAllValues().get(1));
        reset(callback);

        messageCaptor = newMessageCaptor();
        // Receive history between current chat and older received history
        messageHolder.receiveHistoryUpdate(history2, EMPTY_SET, EMPTY_RUNNABLE);
        verify(messageListener, times(2)).messageAdded(messageCaptor.capture(), messageCaptor.capture());
        // first history message inserted before first current chat message
        assertEquals(currentChatMessages.get(0), messageCaptor.getAllValues().get(0));
        assertEquals(history2.get(0), messageCaptor.getAllValues().get(1));
        // second history message inserted before first current chat message
        assertEquals(currentChatMessages.get(0), messageCaptor.getAllValues().get(2));
        assertEquals(history2.get(1), messageCaptor.getAllValues().get(3));
    }

    @Test
    public void receiveHistoryFirstPartOfCurrentChatWhenCurrentChatAndPreviousHistoryAreTracked() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChatMessages);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        holder.receiveHistoryUpdate(history1, EMPTY_SET, EMPTY_RUNNABLE);
        holder.onChatReceive(null, new ChatItem(), currentChatMessages);

        // Request current chat
        tracker.getNextMessages(10, callback);
        // Received current chat
        verify(callback, times(1)).receive(currentChatMessages);
        reset(callback);

        // Request history part
        tracker.getNextMessages(5, callback);
        // Received history part
        verify(callback, times(1)).receive(history1.subList(5, 10));
        reset(callback);

        // Receive history part of current chat
        holder.receiveHistoryUpdate(history2.subList(0, 6), EMPTY_SET, EMPTY_RUNNABLE);
        // No any callbacks called
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
        verifyNoInteractions(messageListener);
        reset(callback);

        // Request remaining history
        tracker.getNextMessages(5, callback);
        verify(callback, times(1)).receive(history1.subList(0, 5));
    }

    @Test
    public void receiveFullHistoryOfCurrentChatWhenOnlyCurrentChatIsTracked() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<List<Message>> listCaptor;

        holder.receiveHistoryUpdate(history1, EMPTY_SET, EMPTY_RUNNABLE);
        holder.onChatReceive(null, new ChatItem(), currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.receiveHistoryUpdate(history2.subList(0, 6), EMPTY_SET, EMPTY_RUNNABLE);
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
        verifyNoInteractions(messageListener);
        reset(callback);

        listCaptor = newListCaptor();
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(listCaptor.capture());
        List<Message> callbackResult = listCaptor.getValue();
        assertFalse(callbackResult.isEmpty());
        assertEquals(history1.subList(10 - callbackResult.size(), 10), callbackResult);
    }

    @Test
    public void receivedHistoryPartOfCurrentChatWhenOnlyCurrentChatIsTracked() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem firstChat = new ChatItem();
        ArgumentCaptor<List<Message>> listCaptor;

        holder.receiveHistoryUpdate(history1, EMPTY_SET, EMPTY_RUNNABLE);
        holder.onChatReceive(null, firstChat, currentChat.subList(0, 6));

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat.subList(0, 6));
        reset(callback);

        holder.receiveHistoryUpdate(history2.subList(0, 6), EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);

        holder.receiveHistoryUpdate(history2.subList(6, 7), EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);

        holder.receiveNewMessage(currentChat.get(6));
        verify(messageListener, times(1)).messageAdded(null, currentChat.get(6));
        reset(messageListener);

        listCaptor = newListCaptor();
        tracker.getNextMessages(5, callback);
        verify(callback, times(1)).receive(listCaptor.capture());
        List<Message> result = listCaptor.getValue();
        assertFalse(result.isEmpty());
        assertEquals(history1.subList(10 - result.size(), 10), result);
    }

    @Test
    public void receiveCurrentChatWhenItsFullPartOfHistoryIsTracked() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem firstChat = new ChatItem();

        holder.receiveHistoryUpdate(concat(history1, history2), EMPTY_SET, EMPTY_RUNNABLE);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history2);
        reset(callback);

        holder.onChatReceive(null, firstChat, currentChat.subList(0, 9));
        verifyNoInteractions(messageListener);

        holder.receiveNewMessage(currentChat.get(9));
        verifyNoInteractions(messageListener);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history1);
    }

    @Test
    public void sequentiallyReceiveLocalHistoryToRemoteHistoryToCurrentChat() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(
            new History(concat(history1, history2)),
            concat(history1, history2.subList(0, 8))
        );
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem firstChat = new ChatItem();

        tracker.getNextMessages(8, callback);
        verify(callback, times(1)).receive(history2.subList(0, 8));
        reset(callback);

        holder.receiveHistoryUpdate(concat(history1, history2.subList(8, 9)), EMPTY_SET, EMPTY_RUNNABLE);
        verify(messageListener, times(1))
            .messageAdded(null, history2.get(8));
        reset(messageListener);

        holder.onChatReceive(null, firstChat, currentChat);
        verify(messageListener, times(1))
            .messageAdded(null, currentChat.get(9));
        reset(messageListener);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history1);
    }

    @Test
    public void receiveCurrentChatWhenItsLastPartOfHistoryIsTracked() {
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(currentChatMessages);
        MessageHolder holder = newMessageHolderWithHistory(new History(history2));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem firstChat = new ChatItem();

        holder.receiveHistoryUpdate(history2.subList(1, 10), EMPTY_SET, EMPTY_RUNNABLE);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history2.subList(1, 10));
        reset(callback);

        holder.onChatReceive(null, firstChat, currentChatMessages);
        verifyNoInteractions(messageListener);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChatMessages.subList(0, 1));
    }

    @Test
    public void receivingChatWithExistingMessagesAndChatMerge() {
        MessageHolder holder = newMessageHolderWithHistory(new History(EMPTY_MESSAGES_LIST));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<Message> messageCaptor;

        ChatItem firstChat = new ChatItem();
        List<MessageImpl> messages = generateCurrentChat(10);

        tracker.getNextMessages(10, callback);
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);

        holder.onChatReceive(null, firstChat, messages.subList(0, 2));
        verify(callback, times(1)).receive(messages.subList(0, 2));
        verify(messageListener, never()).messageAdded(any(Message.class), any(Message.class));
        reset(callback);

        holder.onChatReceive(firstChat, firstChat, messages.subList(0, 2));
        verifyNoInteractions(messageListener);

        messageCaptor = newMessageCaptor();
        holder.onChatReceive(firstChat, firstChat, messages.subList(0, 4));
        verify(messageListener, times(2)).messageAdded(messageCaptor.capture(), messageCaptor.capture());
        verify(messageListener, never()).messageRemoved(any(Message.class));
        verify(messageListener, never()).messageChanged(any(Message.class), any(Message.class));
        assertNull(messageCaptor.getAllValues().get(0));
        assertEquals(messages.get(2), messageCaptor.getAllValues().get(1));
        assertNull(messageCaptor.getAllValues().get(2));
        assertEquals(messages.get(3), messageCaptor.getAllValues().get(3));
        reset(messageListener);

        holder.onChatReceive(firstChat, firstChat, concat(messages.subList(0, 2), messages.subList(3, 5)));
        verify(messageListener, times(1)).messageRemoved(messages.get(2));
        verify(messageListener, times(1)).messageAdded(null, messages.get(4));
    }

    @Test
    public void replacingCurrentChat() {
        MessageHolder holder = newMessageHolderWithHistory(new History(EMPTY_MESSAGES_LIST));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<Message> messageCaptor;

        ChatItem firstChat = new ChatItem("1");
        ChatItem secondChat = new ChatItem("2");
        List<MessageImpl> messages = generateCurrentChat(10);

        tracker.getNextMessages(10, callback);
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);

        holder.onChatReceive(null, firstChat, messages.subList(0, 2));
        verify(callback, times(1)).receive(messages.subList(0, 2));
        verifyNoInteractions(messageListener);
        reset(callback);

        messageCaptor = newMessageCaptor();
        holder.onChatReceive(firstChat, secondChat, messages.subList(2, 5));
        verify(messageListener, times(3)).messageAdded(messageCaptor.capture(), messageCaptor.capture());
        assertNull(messageCaptor.getAllValues().get(0));
        assertEquals(messages.get(2), messageCaptor.getAllValues().get(1));
        assertNull(messageCaptor.getAllValues().get(2));
        assertEquals(messages.get(3), messageCaptor.getAllValues().get(3));
        assertNull(messageCaptor.getAllValues().get(4));
        assertEquals(messages.get(4), messageCaptor.getAllValues().get(5));
        reset(messageListener);

        tracker.resetTo(messages.get(4));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(messages.subList(0, 4));
        reset(callback);

        holder.onChatReceive(secondChat, secondChat, concat(messages.subList(2, 3), messages.subList(4, 6)));
        verify(messageListener, times(1)).messageRemoved(messages.get(3));
        verify(messageListener, times(1)).messageAdded(null, messages.get(5));
        reset(messageListener);

        tracker.resetTo(messages.get(5));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(concat(messages.subList(0, 3), messages.subList(4, 5)));
    }

    @Test
    public void replacingCurrentChatWhenPreviousChatHistoryAlreadyReceived() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> messages = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(messages);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<Message> messageCaptor;

        ChatItem firstChat = new ChatItem("1");
        ChatItem secondChat = new ChatItem("2");

        tracker.getNextMessages(10, callback);
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
        reset(callback);

        holder.onChatReceive(null, firstChat, messages.subList(0, 2));
        verify(callback, times(1)).receive(messages.subList(0, 2));
        verifyNoInteractions(messageListener);
        reset(callback);

        holder.receiveHistoryUpdate(history2.subList(0, 2), EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history1);
        reset(callback);

        messageCaptor = newMessageCaptor();
        holder.onChatReceive(firstChat, secondChat, messages.subList(2, 5));
        verify(messageListener, times(3)).messageAdded(messageCaptor.capture(), messageCaptor.capture());
        assertNull(messageCaptor.getAllValues().get(0));
        assertEquals(messages.get(2), messageCaptor.getAllValues().get(1));
        assertNull(messageCaptor.getAllValues().get(2));
        assertEquals(messages.get(3), messageCaptor.getAllValues().get(3));
        assertNull(messageCaptor.getAllValues().get(4));
        assertEquals(messages.get(4), messageCaptor.getAllValues().get(5));
        resetLastChatMessageIndex(holder);
        reset(messageListener);

        tracker.resetTo(messages.get(4));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(messages.subList(2, 4));
        reset(callback);

        tracker.getNextMessages(20, callback);
        verify(callback, times(1)).receive(concat(history1, history2.subList(0, 2)));
        reset(callback);

        holder.onChatReceive(secondChat, secondChat, concat(messages.subList(2, 3), messages.subList(4, 6)));
        verify(messageListener, times(1)).messageRemoved(messages.get(3));
        reset(messageListener);

        tracker.resetTo(messages.get(5));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(concat(messages.subList(2, 3), messages.subList(4, 5)));
    }

    @Test
    public void replacingCurrentChatWhenPreviousChatHistoryStoredLocally() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> messages = generateCurrentChat(10);
        List<MessageImpl> history2 = generateHistoryFromCurrentChat(messages);
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)), history2.subList(0, 2));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ArgumentCaptor<Message> messageCaptor;

        ChatItem firstChat = new ChatItem("1");
        ChatItem secondChat = new ChatItem("2");

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history2.subList(0, 2));
        reset(callback);

        holder.onChatReceive(null, firstChat, messages.subList(0, 2));
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
        verifyNoInteractions(messageListener);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history1);
        reset(callback);

        messageCaptor = newMessageCaptor();
        holder.onChatReceive(firstChat, secondChat, messages.subList(2, 5));
        verify(messageListener, times(3)).messageAdded(messageCaptor.capture(), messageCaptor.capture());
        assertNull(messageCaptor.getAllValues().get(0));
        assertEquals(messages.get(2), messageCaptor.getAllValues().get(1));
        assertNull(messageCaptor.getAllValues().get(2));
        assertEquals(messages.get(3), messageCaptor.getAllValues().get(3));
        assertNull(messageCaptor.getAllValues().get(4));
        assertEquals(messages.get(4), messageCaptor.getAllValues().get(5));
        resetLastChatMessageIndex(holder);
        reset(messageListener);

        tracker.resetTo(messages.get(4));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(messages.subList(2, 4));
        reset(callback);

        tracker.getNextMessages(20, callback);
        verify(callback, times(1)).receive(concat(history1, history2.subList(0, 2)));
        reset(callback);

        holder.onChatReceive(secondChat, secondChat, concat(messages.subList(2, 3), messages.subList(4, 6)));
        verify(messageListener, times(1)).messageRemoved(messages.get(3));
        verify(messageListener, times(1)).messageAdded(null, messages.get(5));
        reset(messageListener);

        tracker.resetTo(messages.get(5));
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(concat(messages.subList(2, 3), messages.subList(4, 5)));
    }

    @Test
    public void mixedCurrentChatAndHistoryReset() {
        List<MessageImpl> history1 = generateHistory(10);
        List<MessageImpl> history2 = generateHistory(10);
        List<MessageImpl> currentChat = generateCurrentChat(10);
        MessageImpl nextCurrentChatMessage = newCurrentChatMessage();
        MessageHolder holder = newMessageHolderWithHistory(new History(concat(history1, history2)));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem firstChat = new ChatItem();
        ArgumentCaptor<List<Message>> listCaptor;

        holder.receiveHistoryUpdate(history2, EMPTY_SET, EMPTY_RUNNABLE);
        holder.onChatReceive(null, firstChat, currentChat);

        listCaptor = newListCaptor();
        tracker.getNextMessages(10, callback);
        tracker.getNextMessages(10, callback);
        tracker.getNextMessages(10, callback);
        verify(callback, times(3)).receive(listCaptor.capture());
        assertEquals(currentChat, listCaptor.getAllValues().get(0));
        assertEquals(history2, listCaptor.getAllValues().get(1));
        assertEquals(history1, listCaptor.getAllValues().get(2));
        reset(callback);

        holder.receiveNewMessage(nextCurrentChatMessage);
        verify(messageListener, times(1)).messageAdded(null, nextCurrentChatMessage);
        reset(messageListener);

        listCaptor = newListCaptor();
        tracker.resetTo(nextCurrentChatMessage);
        tracker.getNextMessages(10, callback);
        tracker.getNextMessages(10, callback);
        tracker.getNextMessages(10, callback);
        verify(callback, times(3)).receive(listCaptor.capture());
        assertEquals(currentChat, listCaptor.getAllValues().get(0));
        assertEquals(history2, listCaptor.getAllValues().get(1));
        assertEquals(history1, listCaptor.getAllValues().get(2));
        reset(callback);
    }

    @Test
    public void emptyHistoryAndReceiveNewMessage() {
        MessageImpl nextCurrentChatMessage = newCurrentChatMessage();
        MessageHolder holder = newMessageHolderWithHistory(new History(EMPTY_MESSAGES_LIST));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        holder.receiveHistoryUpdate(EMPTY_MESSAGES_LIST, EMPTY_SET, EMPTY_RUNNABLE);
        holder.setReachedEndOfRemoteHistory(true);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(EMPTY_MESSAGES_LIST);
        reset(callback);

        holder.receiveNewMessage(nextCurrentChatMessage);
        verify(messageListener, times(1)).messageAdded(null, nextCurrentChatMessage);
    }

    @Test
    public void currentChatMessageEdition() {
        MessageImpl nextCurrentChatMessage = newCurrentChatMessage();
        MessageImpl editedCurrentChatMessage = newEditedCurrentChatMessage(nextCurrentChatMessage);
        MessageHolder holder = newMessageHolderWithHistory(new History(EMPTY_MESSAGES_LIST));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        holder.receiveHistoryUpdate(EMPTY_MESSAGES_LIST, EMPTY_SET, EMPTY_RUNNABLE);
        holder.setReachedEndOfRemoteHistory(true);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(EMPTY_MESSAGES_LIST);
        reset(callback);

        holder.receiveNewMessage(nextCurrentChatMessage);
        verify(messageListener, times(1)).messageAdded(null, nextCurrentChatMessage);
        reset(messageListener);

        holder.onMessageChanged(editedCurrentChatMessage);
        verify(messageListener, times(1)).messageChanged(nextCurrentChatMessage, editedCurrentChatMessage);
    }

    @Test
    public void currentChatEditionAndReceivedWithFullUpdate() {
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> editedCurrentChatMessage = cloneCurrentChatMessages(currentChat);
        currentChat.set(9, newEditedCurrentChatMessage(currentChat.get(9)));
        MessageHolder holder = newMessageHolderWithHistory(new History(EMPTY_MESSAGES_LIST));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.receiveHistoryUpdate(EMPTY_MESSAGES_LIST, EMPTY_SET, EMPTY_RUNNABLE);
        holder.setReachedEndOfRemoteHistory(true);
        holder.onChatReceive(null, chat, currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.onChatReceive(chat, chat, editedCurrentChatMessage);
        verify(messageListener, times(1))
            .messageChanged(currentChat.get(9), editedCurrentChatMessage.get(9));
    }

    @Test
    public void replaceHistoryMessageWithEditedCurrentChat() {
        List<MessageImpl> currentChatMessages = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChatMessages);
        currentChatMessages.set(9, newEditedCurrentChatMessage(currentChatMessages.get(9)));
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);
        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history);
        reset(callback);

        holder.onChatReceive(null, new ChatItem(), currentChatMessages);
        verify(messageListener, times(1))
            .messageChanged(history.get(9), currentChatMessages.get(9));
    }

    @Test
    public void replaceCurrentChatMessageWithEditedHistory() {
        // (chat closed -> history received -> history edited)
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        MessageImpl editedHistory = newEditedHistoryMessage(history.get(9));
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.onChatReceive(null, chat, currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.onChatReceive(chat, null, EMPTY_MESSAGES_LIST);
        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);
        verify(messageListener, times(20)).messageChanged(any(Message.class), any(Message.class));
        reset(messageListener);

        holder.receiveHistoryUpdate(Collections.singletonList(editedHistory), EMPTY_SET, EMPTY_RUNNABLE);
        verify(messageListener, never()).messageChanged(any(Message.class), any(Message.class));
    }

    @Test
    public void replaceCurrentChatMessageWithEditedHistory2() {
        // (chat still open -> history received -> history edited)
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        MessageImpl editedHistory = newEditedHistoryMessage(history.get(9));
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.onChatReceive(null, chat, currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);

        holder.receiveHistoryUpdate(Collections.singletonList(editedHistory), EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);
    }

    @Test
    public void replaceCurrentChatMessageWithEditedHistory3() {
        // (chat closed -> received edited history)
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        history.set(9, newEditedHistoryMessage(history.get(9)));
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.onChatReceive(null, chat, currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.onChatReceive(chat, null, EMPTY_MESSAGES_LIST);
        verify(messageListener, times(10)).messageChanged(any(Message.class), any(Message.class));
        reset(messageListener);

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);
        verify(messageListener, times(1)).messageChanged(history.get(9), history.get(9));
    }

    @Test
    public void replaceCurrentChatMessageWithEditedHistory4() {
        // (received edited history -> chat closed)
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        history.set(9, newEditedHistoryMessage(history.get(9)));
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.onChatReceive(null, chat, currentChat);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(currentChat);
        reset(callback);

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);
        verifyNoInteractions(messageListener);

        holder.onChatReceive(chat, null, EMPTY_MESSAGES_LIST);
        verify(messageListener, times(1)).messageChanged(currentChat.get(9), history.get(9));
    }

    @Test
    public void receivingCurrentChatMessageWhenItIsHeldAsLocalHistory() {
        List<MessageImpl> currentChat = generateCurrentChat(10);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);

        tracker.getNextMessages(10, callback);
        verify(callback, times(1)).receive(history);
        reset(callback);

        holder.receiveNewMessage(currentChat.get(9));
        holder.receiveNewMessage(currentChat.get(9));
        verify(callback, never()).receive(EMPTY_MESSAGES_LIST);
    }

    @Test
    public void replacingHistoryWithCurrentChatDeletingMessagesReplacingItWithChatAgain() {
        List<MessageImpl> currentChat = generateCurrentChat(1);
        List<MessageImpl> history = generateHistoryFromCurrentChat(currentChat);
        MessageHolder holder = newMessageHolderWithHistory(new History(history));
        MessageListener messageListener = mock(MessageListener.class);
        MessageTracker tracker = holder.newMessageTracker(messageListener);
        MessageTracker.GetMessagesCallback callback = mock(MessageTracker.GetMessagesCallback.class);
        ChatItem chat = new ChatItem();

        holder.receiveHistoryUpdate(history, EMPTY_SET, EMPTY_RUNNABLE);

        tracker.getNextMessages(1, callback);
        verify(callback, times(1)).receive(history);
        reset(callback);

        holder.onChatReceive(null, chat, currentChat);
        verify(messageListener, never()).messageChanged(any(Message.class), any(Message.class));
        reset(messageListener);

        holder.onMessageDeleted(currentChat.get(0).getIdInCurrentChat());
        verify(messageListener, times(1)).messageRemoved(any(Message.class));
        reset(messageListener);

        holder.onChatReceive(chat, chat, currentChat);
        verify(messageListener, times(1)).messageAdded(null, currentChat.get(0));
    }
}