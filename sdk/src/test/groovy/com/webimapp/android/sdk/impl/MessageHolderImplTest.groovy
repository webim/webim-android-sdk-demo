package com.webimapp.android.sdk.impl

import com.webimapp.android.sdk.Message
import com.webimapp.android.sdk.MessageListener
import com.webimapp.android.sdk.MessageTracker
import com.webimapp.android.sdk.impl.items.ChatItem
import groovy.transform.Canonical
import spock.lang.Specification

import static java.lang.Math.max
import static java.util.Collections.emptyList
import static java.util.Collections.emptySet

class MessageHolderImplTest extends Specification {
    @Canonical
    static class History implements RemoteHistoryProvider {
        List<MessageImpl> history

        @Override
        void requestHistoryBefore(long beforeMessageTS, HistoryBeforeCallback callback) {
            int before = history.findLastIndexOf{it.getTimeMicros() <= beforeMessageTS}
            int after = max(0, before - 100)
            callback.onSuccess(before <= 0 ? emptyList() : history.subList(max(0, before - 100), before), after != 0)
        }
    }

    private int msgIndCounter = 0

    List<MessageImpl> genHistory(int count) {
        def history = []
        for (int i in msgIndCounter ..< msgIndCounter+count) {
            history << new MessageImpl("",
                    StringId.forMessage(i as String),
                    StringId.forOperator("op"),
                    "",
                    "",
                    Message.Type.OPERATOR,
                    "text",
                    i,
                    null,
                    i as String,
                    null,
                    true,
                    null,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null)
        }
        msgIndCounter += count
        history
    }

    List<MessageImpl> generateCurrentChat(int count) {
        def curChat = []
        for (int i in msgIndCounter ..< msgIndCounter+count) {
            curChat << new MessageImpl("",
                    StringId.forMessage(i as String),
                    StringId.forOperator("op"),
                    "",
                    "",
                    Message.Type.OPERATOR,
                    "text",
                    i,
                    null,
                    i as String,
                    null,
                    false,
                    null,
                    false,
                    true,
                    false,
                    null,
                    null,
                    null)
        }
        msgIndCounter += count
        curChat
    }

    List<MessageImpl> generateHistoryFromCurrentChat(List<? extends MessageImpl> input) {
        return input.collect {
            new MessageImpl("",
                    it.getId(),
                    it.getOperatorId(),
                    it.getAvatarUrlLastPart(),
                    it.getSenderName(),
                    it.getType(),
                    it.getText(),
                    it.timeMicros,
                    null,
                    it.timeMicros as String,
                    null,
                    true,
                    null,
                    false,
                    true,
                    false,
                    null,
                    null,
                    null)
        }
    }

    MessageImpl newCurrentChat() {
        int i = msgIndCounter++
        new MessageImpl("",
                StringId.forMessage(i as String),
                StringId.forOperator("op"),
                "",
                "",
                Message.Type.OPERATOR,
                "text",
                i,
                null,
                i as String,
                null,
                false,
                null,
                false,
                true,
                false,
                null,
                null,
                null)
    }

    MessageImpl newEditedCurrentChat(MessageImpl orig) {
        new MessageImpl("",
                orig.getId(),
                orig.getOperatorId(),
                orig.getAvatarUrlLastPart(),
                orig.getSenderName(),
                orig.getType(),
                orig.getText() + "1",
                orig.getTimeMicros(),
                null,
                orig.getIdInCurrentChat(),
                null,
                false,
                null,
                false,
                true,
                false,
                null,
                null,
                null)
    }

    MessageImpl newEditedHistory(MessageImpl orig) {
        new MessageImpl("",
                orig.getId(),
                orig.getOperatorId(),
                orig.getAvatarUrlLastPart(),
                orig.getSenderName(),
                orig.getType(),
                orig.getText() + "1",
                orig.getTimeMicros(),
                null,
                orig.getHistoryId().getDbId(),
                null,
                true,
                null,
                false,
                true,
                false,
                null,
                null,
                null)
    }

    MessageHolderImpl newMessageHolderWithHistory(RemoteHistoryProvider history) {
        new MessageHolderImpl(AccessChecker.EMPTY, history, new MemoryHistoryStorage(), false)
    }

    MessageHolderImpl newMessageHolderWithHistory(RemoteHistoryProvider history, List<MessageImpl> localHistory) {
        new MessageHolderImpl(AccessChecker.EMPTY, history, new MemoryHistoryStorage(localHistory), false)
    }

    Message wrap(Message toWrap) {
        toWrap
    }

    List<Message> wrap(List<Message> toWrap) {
        toWrap.collect {wrap it}
    }



    def "Internal (test) history generation"() {
        when:
        def history1 = genHistory(10)
        def history2 = generateCurrentChat(10)
        def history3 = genHistory(10)
        then:
        history1.collect{it.primaryId} == (0..9).collect{it as String}
        history3.collect{it.primaryId} == (20..29).collect{it as String}
    }

    def "MessageTracker.getNextMessages() respond current chat messages immediately (if exists)"() {
        setup:
        def currentChat = generateCurrentChat(10)
        def holder = newMessageHolderWithHistory(new History([]))
        holder.currentChatMessages.addAll(currentChat)
        def tracker = holder.newMessageTracker(Mock(MessageListener))
        def callback = Mock(MessageTracker.GetMessagesCallback)

        when: "request 10 (all) messages"
        tracker.getNextMessages(10, callback)
        then: "current chat received immediately"
        1 * callback.receive(currentChat)
    }

    def "MessageTracker.getNextMessages() await history response"() {
        setup:
        def history1 = genHistory(10)
        def history2 = genHistory(10)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def tracker = holder.newMessageTracker(Mock(MessageListener))
        def callback = Mock(MessageTracker.GetMessagesCallback)

        when: "request 10 messages"
        tracker.getNextMessages(10, callback)
        then: "no callback called because history has not been received; callback was cached an will be called on history receive"
        0 * callback.receive(*_)

        when: "history receive"
        holder.receiveHistoryUpdate(history2, emptySet(), {})
        then: "called previously cached callback"
        1 * callback.receive(wrap(history2))

        when: "request next 10 messages"
        tracker.getNextMessages(10, callback)
        then: "received older history"
        1 * callback.receive(wrap(history1))
    }

    def "MessageTracker.getNextMessages() await history response (with current chat)"() {
        setup:
        def history1 = genHistory(10)
        def history2 = genHistory(10)
        def currentChat = generateCurrentChat(10)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        holder.currentChatMessages.addAll(currentChat)
        def tracker = holder.newMessageTracker(Mock(MessageListener))
        def callback = Mock(MessageTracker.GetMessagesCallback)

        when:
        tracker.getNextMessages(currentChat.size(), callback)
        then:
        1 * callback.receive(wrap(currentChat))

        when:
        tracker.getNextMessages(10, callback)
        then:
        0 * callback.receive(*_)

        when:
        holder.receiveHistoryUpdate(history2, emptySet(), {})
        then:
        1 * callback.receive(wrap(history2))

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(history1))
    }

    def "Insert history between older history and current chat (messages must be inserted)"() {
        setup:
        def history1 = genHistory(10)
        def history2 = genHistory(2)
        def curChat = generateCurrentChat(10)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history1, emptySet(), {})
        holder.onChatReceive(null, new ChatItem(), curChat)

        when:
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))
        1 * callback.receive(wrap(history1))

        when: "receive history between current chat end older received history"
        holder.receiveHistoryUpdate(history2, emptySet(), {})
        then: "first history message inserted before first current chat message"
        1 * listener.messageAdded(wrap(curChat[0]), wrap(history2[0]))
        then: "second history message inserted before first current chat message"
        1 * listener.messageAdded(wrap(curChat[0]), wrap(history2[1]))
    }

    def "receive history first part of current chat when current chat and previous history are tracked (any callbacks must not be called)"() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history1, emptySet(), {})
        holder.onChatReceive(null, new ChatItem(), curChat)

        when: "request current chat"
        tracker.getNextMessages(10, callback)
        then: "received current chat"
        1 * callback.receive(wrap(curChat))

        when: "request history part"
        tracker.getNextMessages(5, callback)
        then: "received history part"
        1 * callback.receive(wrap(history1[5..9]))

        when: "receive history part of current chat"
        holder.receiveHistoryUpdate(history2[0..5], emptySet(), {})
        then: "no any callbacks called"
        0 * callback.receive(*_)
        0 * listener._

        when: "request remaining history"
        tracker.getNextMessages(5, callback)
        then:
        1 * callback.receive(wrap(history1[0..4]))
    }

    def "receive full history of current chat when only current chat is tracked (any callbacks must not be called)"() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history1, emptySet(), {})
        holder.onChatReceive(null, new ChatItem(), curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when: "receive history part of current chat"
        holder.receiveHistoryUpdate(history2[0..5], emptySet(), {})
        then: "no any callbacks called"
        0 * callback.receive(*_)
        0 * listener._

        when: "request more messages"
        tracker.getNextMessages(10, callback)
        then: "received part of history before current chat; history part of current chat was ignored"
        1 * callback.receive({!it.isEmpty() && it == wrap(history1.subList(10 - it.size(), 10))})
    }

    def "receive history last part of current chat when only current chat is tracked"() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history2[5..9], emptySet(), {})
        holder.onChatReceive(null, new ChatItem(), curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when: "request more messages"
        tracker.getNextMessages(10, callback)
        then: "received part of history before current chat; history part of current chat was ignored"
        1 * callback.receive({!it.isEmpty() && it == wrap(history1.subList(10 - it.size(), 10))})

        when:
        tracker.resetTo(wrap(curChat[9]))
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        1+1
        then:
        1 * callback.receive(wrap(curChat[0..8]))
        1 * callback.receive(wrap(history1))
    }

    def """receive history part of current chat when only current chat is tracked.
    request exactly as many messages as received with history for current chat - 5 (other branch used)"""() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def firstChat = new ChatItem()

        holder.receiveHistoryUpdate(history1, emptySet(), {})
        holder.onChatReceive(null, firstChat, curChat[0..5])

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat[0..5]))

        when: "receive history part of current chat"
        holder.receiveHistoryUpdate(history2[0..5], emptySet(), {})
        then: "no any callbacks called"
        0 * listener._

        when: "receive history part over the current chat"
        holder.receiveHistoryUpdate(history2[6..6], emptySet(), {})
        then: "no any callbacks called (awaiting current chat)"
        0 * listener._

        when:
        holder.receiveNewMessage(curChat[6])
        then:
        1 * listener.messageAdded(null, wrap(curChat[6]))

        when: "request more messages"
        tracker.getNextMessages(5, callback)
        then: "received part of history before current chat; history part of current chat was ignored"
        1 * callback.receive({!it.isEmpty() && it == wrap(history1.subList(10 - it.size(), 10))})
    }

    def "receive current chat when its full part of history is tracked"() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def firstChat = new ChatItem()

        holder.receiveHistoryUpdate(history1 + history2, emptySet(), {})

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(history2))

        when:
        holder.onChatReceive(null, firstChat, curChat[0..8])
        then:
        0 * listener._

        when:
        holder.receiveNewMessage(curChat[9])
        then:
        0 * listener._

        when: "request more messages"
        tracker.getNextMessages(10, callback)
        then: "received older history"
        1 * callback.receive(wrap(history1))
    }

    def "sequentially receive: local history -> remote history -> current chat"() {
        setup:
        def history1 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history1 + history2), history1 + history2[0..7])
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def firstChat = new ChatItem()

        when:
        tracker.getNextMessages(8, callback)
        then:
        1 * callback.receive(wrap(history2[0..7]))

        when:
        holder.receiveHistoryUpdate(history1 + history2[8..8], emptySet(), {})
        then:
        1 * listener.messageAdded(null, wrap(history2[8]))
        0 * listener._

        when:
        holder.onChatReceive(null, firstChat, curChat)
        then:
        1 * listener.messageAdded(null, wrap(curChat[9]))
        0 * listener._

        when: "request more messages"
        tracker.getNextMessages(10, callback)
        then: "received older history"
        1 * callback.receive(wrap(history1))
    }

    def "receive current chat when its last part of history is tracked"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(curChat)
        def holder = newMessageHolderWithHistory(new History(history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def firstChat = new ChatItem()

        holder.receiveHistoryUpdate(history2[1..9], emptySet(), {})

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(history2[1..9]))

        when:
        holder.onChatReceive(null, firstChat, curChat)
        then:
        0 * listener._

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat[0..0]))
    }

    def "Receiving chat with existing messages, chat merge"() {
        setup:
        def holder = newMessageHolderWithHistory(new History([]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        def firstChat = new ChatItem()
        def messages = generateCurrentChat(10)

        when: "request some messages"
        tracker.getNextMessages(10, callback)
        then: "no callback called, because no messages. Callback was cached and will be called on message receive"
        0 * callback.receive(*_)

        when: "receive chat with 2 messages"
        holder.onChatReceive(null, firstChat, messages[0..1])
        then: "2 messages posted to previously cached callback. Listener methods was not called"
        1 * callback.receive(wrap(messages[0..1]))
        0 * listener.messageAdded(*_)

        when: "receive chat with exact the same messages"
        holder.onChatReceive(firstChat, firstChat, messages[0..1])
        then: "no any callbacks called"
        0 * listener._

        when: "receive chat with more messages"
        holder.onChatReceive(firstChat, firstChat, messages[0..3])
        then:
        1 * listener.messageAdded(null, wrap(messages[2]))
        then:
        1 * listener.messageAdded(null, wrap(messages[3]))
        and:
        0 * listener.messageRemoved(*_)
        0 * listener.messageChanged(*_)

        when:
        holder.onChatReceive(firstChat, firstChat, messages[0..1] + messages[3..4])
        then:
        1 * listener.messageRemoved(wrap(messages[2]))
        then:
        1 * listener.messageAdded(null,wrap( messages[4]))
        and:
        0 * listener.messageRemoved(*_)
        0 * listener.messageAdded(*_)
    }

    def "Replacing current chat"() {
        setup:
        def holder = newMessageHolderWithHistory(new History([]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        def firstChat = new ChatItem("1")
        def secondChat = new ChatItem("2")
        def messages = generateCurrentChat(10)

        when: "request some messages"
        tracker.getNextMessages(10, callback)
        then: "no callback called, because no messages. Callback was cached and will be called on message receive"
        0 * callback.receive(*_)

        when: "receive 1st chat with 2 messages"
        holder.onChatReceive(null, firstChat, messages[0..1])
        then: "received 1st message"
        1 * callback.receive(wrap(messages[0..1]))
        0 * listener._

        when: "receive 2nd chat with next 3 messages"
        holder.onChatReceive(firstChat, secondChat, messages[2..4])
        then: "received 3rd message"
        1 * listener.messageAdded(null, wrap(messages[2]))
        then: "received 4th message"
        1 * listener.messageAdded(null, wrap(messages[3]))
        then: "received 5th message"
        1 * listener.messageAdded(null, wrap(messages[4]))

        when: "reset to last message and requesting latest messages"
        tracker.resetTo(wrap(messages[4]))
        tracker.getNextMessages(10, callback)
        then: "received messages [0123] (current chat messages is now [01234])"
        1 * callback.receive(wrap(messages[0..3]))

        when: "receive second chat again, with 4th message deleted and 6th added"
        holder.onChatReceive(secondChat, secondChat, messages[2..2] + messages[4..5])
        then: "received 4th message deletion"
        1 * listener.messageRemoved(wrap(messages[3]))
        then: "5th say exists"
        0 * listener.messageRemoved(null, wrap(messages[4]))
        0 * listener.messageAdded(null, wrap(messages[4]))
        then: "received 6th message"
        1 * listener.messageAdded(null, wrap(messages[5]))

        when:
        tracker.resetTo(wrap(messages[5]))
        tracker.getNextMessages(10, callback)
        then: "messages is now [01245]"
        1 * callback.receive(wrap(messages[0..2] + messages[4..4]))
    }

    def "Replacing current chat when previous chat history already received"() {
        setup:
        def history1 = genHistory(10)
        def messages = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(messages)
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        def firstChat = new ChatItem("1")
        def secondChat = new ChatItem("2")

        when: "request some messages"
        tracker.getNextMessages(10, callback)
        then: "no callback called, because no messages. Callback was cached and will be called on message receive"
        0 * callback.receive(*_)

        when: "receive 1st chat with 2 messages"
        holder.onChatReceive(null, firstChat, messages[0..1])
        then: "received 1st message"
        1 * callback.receive(wrap(messages[0..1]))
        0 * listener._

        when: "receive history part of current chat"
        holder.receiveHistoryUpdate(history2[0..1], emptySet(), {})
        then: "no callbacks called"
        0 * listener._

        when: "request all messages"
        tracker.getNextMessages(10, callback)
        then: "received full history"
        1 * callback.receive(wrap(history1))

        when: "receive 2nd chat with next 3 messages"
        holder.onChatReceive(firstChat, secondChat, messages[2..4])
        then: "received 3rd message"
        1 * listener.messageAdded(null, wrap(messages[2]))
        then: "received 4th message"
        1 * listener.messageAdded(null, wrap(messages[3]))
        then: "received 5th message"
        1 * listener.messageAdded(null, wrap(messages[4]))
        and: "all current chat messages was historified"
        holder.lastChatIndex == 0

        when: "reset to last message and request latest messages"
        tracker.resetTo(wrap(messages[4]))
        tracker.getNextMessages(10, callback)
        then: "receive messages [23] (current chat messages is now [234])"
        1 * callback.receive(wrap(messages[2..3]))

        when: "request all messages"
        tracker.getNextMessages(20, callback)
        then: "received full history"
        1 * callback.receive(wrap(history1 + history2[0..1]))

        when: "receive second chat again, with 4th message deleted and 6th added"
        holder.onChatReceive(secondChat, secondChat, messages[2..2] + messages[4..5])
        then: "received 4th message deletion"
        1 * listener.messageRemoved(wrap(messages[3]))
        then: "5th say exists"
        0 * listener.messageRemoved(null, wrap(messages[4]))
        0 * listener.messageAdded(null, wrap(messages[4]))
        then: "received 6th message"
        1 * listener.messageAdded(null, wrap(messages[5]))

        when:
        tracker.resetTo(wrap(messages[5]))
        tracker.getNextMessages(10, callback)
        then: "received messages [24] (current chat messages is now [245])"
        1 * callback.receive(wrap(messages[2..2] + messages[4..4]))
    }

    def "Replacing current chat when previous chat history stored locally"() {
        setup:
        def history1 = genHistory(10)
        def messages = generateCurrentChat(10)
        def history2 = generateHistoryFromCurrentChat(messages)
        def holder = newMessageHolderWithHistory(new History(history1 + history2), history2[0..1])
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        def firstChat = new ChatItem("1")
        def secondChat = new ChatItem("2")

        when: "request some messages"
        tracker.getNextMessages(10, callback)
        then: "no callback called, because no messages. Callback was cached and will be called on message receive"
        1 * callback.receive(wrap(history2[0..1]))

        when: "receive 1st chat with 2 messages"
        holder.onChatReceive(null, firstChat, messages[0..1])
        then: "received 1st message"
        0 * callback.receive(*_)
        0 * listener._

        when: "request all messages"
        tracker.getNextMessages(10, callback)
        then: "received full history"
        1 * callback.receive(wrap(history1))

        when: "receive 2nd chat with next 3 messages"
        holder.onChatReceive(firstChat, secondChat, messages[2..4])
        then: "received 3rd message"
        1 * listener.messageAdded(null, wrap(messages[2]))
        then: "received 4th message"
        1 * listener.messageAdded(null, wrap(messages[3]))
        then: "received 5th message"
        1 * listener.messageAdded(null, wrap(messages[4]))
        and: "all current chat messages was historified"
        holder.lastChatIndex == 0

        when: "reset to last message and request latest messages"
        tracker.resetTo(wrap(messages[4]))
        tracker.getNextMessages(10, callback)
        then: "receive messages [23] (current chat messages is now [234])"
        1 * callback.receive(wrap(messages[2..3]))

        when: "request all messages"
        tracker.getNextMessages(20, callback)
        then: "received full history"
        1 * callback.receive(wrap(history1 + history2[0..1]))

        when: "receive second chat again, with 4th message deleted and 6th added"
        holder.onChatReceive(secondChat, secondChat, messages[2..2] + messages[4..5])
        then: "received 4th message deletion"
        1 * listener.messageRemoved(wrap(messages[3]))
        then: "5th say exists"
        0 * listener.messageRemoved(null, wrap(messages[4]))
        0 * listener.messageAdded(null, wrap(messages[4]))
        then: "received 6th message"
        1 * listener.messageAdded(null, wrap(messages[5]))

        when:
        tracker.resetTo(wrap(messages[5]))
        tracker.getNextMessages(10, callback)
        then: "received messages [24] (current chat messages is now [245])"
        1 * callback.receive(wrap(messages[2..2] + messages[4..4]))
    }

    def "Mixed current chat & history reset"() {
        setup:
        def history1 = genHistory(10)
        def history2 = genHistory(10)
        def curChat = generateCurrentChat(10)
        def nextCurChat = newCurrentChat()
        def holder = newMessageHolderWithHistory(new History(history1 + history2))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def firstChat = new ChatItem()

        holder.receiveHistoryUpdate(history2, emptySet(), {})
        holder.onChatReceive(null, firstChat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))
        1 * callback.receive(wrap(history2))
        1 * callback.receive(wrap(history1))

        when:
        holder.receiveNewMessage(nextCurChat)
        then:
        1 * listener.messageAdded(null, wrap(nextCurChat))

        when:
        tracker.resetTo(wrap(nextCurChat))
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))
        1 * callback.receive(wrap(history2))
        1 * callback.receive(wrap(history1))
    }

    def "empty history, receive new message"() {
        setup:
        def nextCurChat = newCurrentChat()
        def holder = newMessageHolderWithHistory(new History([]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate([], emptySet(), {})
        holder.setReachedEndOfRemoteHistory(true)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive([])

        when:
        holder.receiveNewMessage(nextCurChat)
        then:
        1 * listener.messageAdded(null, wrap(nextCurChat))
    }

    def "current chat message edition"() {
        setup:
        def nextCurChat = newCurrentChat()
        def editedCurChat = newEditedCurrentChat(nextCurChat)
        def holder = newMessageHolderWithHistory(new History([]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate([], emptySet(), {})
        holder.setReachedEndOfRemoteHistory(true)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive([])

        when:
        holder.receiveNewMessage(nextCurChat)
        then:
        1 * listener.messageAdded(null, wrap(nextCurChat))

        when:
        holder.onMessageChanged(editedCurChat)
        then:
        1 * listener.messageChanged(wrap(nextCurChat), wrap(editedCurChat))
    }

    def "current chat message edition, received with full update"() {
        setup:
        def curChat = generateCurrentChat(10)
        def editedCurChat = curChat.clone()
        curChat[9] = newEditedCurrentChat(curChat[9])
        def holder = newMessageHolderWithHistory(new History([]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()

        holder.receiveHistoryUpdate([], emptySet(), {})
        holder.setReachedEndOfRemoteHistory(true)
        holder.onChatReceive(null, chat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(curChat)

        when:
        holder.onChatReceive(chat, chat, editedCurChat)
        then:
        1 * listener.messageChanged(wrap(curChat[9]), wrap(editedCurChat[9]))
        0 * listener._
    }

    def "history message edition"() {
        setup:
        def history = genHistory(10)
        def editedHistory = newEditedHistory(history[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history, emptySet(), {})

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(history))

        when:
        holder.receiveHistoryUpdate([editedHistory], emptySet(), {})
        then:
        1 * listener.messageChanged(wrap(history[9]), wrap(editedHistory))
    }

    def "replace history message with edited current chat"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(curChat)
        curChat[9] = newEditedCurrentChat(curChat[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)

        holder.receiveHistoryUpdate(history, emptySet(), {})

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(history))

        when:
        holder.onChatReceive(null, new ChatItem(), curChat)
        then:
        1 * listener.messageChanged(wrap(history[9]), wrap(curChat[9]))
    }

    def "replace current chat message with edited history (chat closed -> history received -> history edited)"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(curChat)
        def editedHistory = newEditedHistory(history[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()

        holder.onChatReceive(null, chat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when:
        holder.onChatReceive(chat, null, [])
        holder.receiveHistoryUpdate(history, emptySet(), {})
        then:
        20 * listener._

        when:
        holder.receiveHistoryUpdate([editedHistory], emptySet(), {})
        then:
        0 * listener.messageChanged(wrap(curChat[9]), wrap(editedHistory))
    }

    def "replace current chat message with edited history (chat still open -> history received -> history edited)"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(curChat)
        def editedHistory = newEditedHistory(history[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()

        holder.onChatReceive(null, chat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when:
        holder.receiveHistoryUpdate(history, emptySet(), {})
        then:
        0 * listener._

        when:
        holder.receiveHistoryUpdate([editedHistory], emptySet(), {})
        then: "while current chat exists, history changes have no effect"
        0 * listener._
    }

    def "replace current chat message with edited history (chat closed -> received edited history)"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(curChat)
        history[9] = newEditedHistory(history[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()

        holder.onChatReceive(null, chat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when:
        holder.onChatReceive(chat, null, [])
        then:
        10 * listener._

        when:
        holder.receiveHistoryUpdate(history, emptySet(), {})
        then:
        1 * listener.messageChanged(wrap(curChat[9]), wrap(history[9]))
    }

    def "replace current chat message with edited history (received edited history -> chat closed)"() {
        setup:
        def curChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(curChat)
        history[9] = newEditedHistory(history[9])
        def holder = newMessageHolderWithHistory(new History([history]))
        def listener = Mock(MessageListener)
        def tracker = holder.newMessageTracker(listener)
        def callback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()

        holder.onChatReceive(null, chat, curChat)

        when:
        tracker.getNextMessages(10, callback)
        then:
        1 * callback.receive(wrap(curChat))

        when:
        holder.receiveHistoryUpdate(history, emptySet(), {})
        then:
        0 * listener._

        when:
        holder.onChatReceive(chat, null, [])
        then:
        1 * listener.messageChanged(wrap(curChat[9]), wrap(history[9]))
    }

    def "Receiving current chat message when it is holded as local history"() {
        setup:
        def currentChat = generateCurrentChat(10)
        def history = generateHistoryFromCurrentChat(currentChat)
        def messageHolder = newMessageHolderWithHistory(new History([history]))
        def messageListener = Mock(MessageListener)
        def messageTracker = messageHolder.newMessageTracker(messageListener)
        def getMessagesCallback = Mock(MessageTracker.GetMessagesCallback)
        messageHolder.receiveHistoryUpdate(history, emptySet(), { })

        when: "Requesting messages"
        messageTracker.getNextMessages(10, getMessagesCallback)
        then: "History messages should be received"
        1 * getMessagesCallback.receive(wrap(history))

        when: "Received current chat message twice which is holded as local history"
        messageHolder.receiveNewMessage(currentChat[9])
        messageHolder.receiveNewMessage(currentChat[9])
        then: "Message shouldn't be received as new"
        _ * getMessagesCallback._
    }

    def "Replacing history with current chat, deleting messages, replacing it with chat again" () {
        setup:
        def currentChat = generateCurrentChat(1)
        def history = generateHistoryFromCurrentChat(currentChat)
        def messageHolder = newMessageHolderWithHistory(new History([history]))
        def messageListener = Mock(MessageListener)
        def messageTracker = messageHolder.newMessageTracker(messageListener)
        def getMessagesCallback = Mock(MessageTracker.GetMessagesCallback)
        def chat = new ChatItem()
        messageHolder.receiveHistoryUpdate(history, emptySet(), { })

        when: "Requesting messages"
        messageTracker.getNextMessages(1, getMessagesCallback)
        then: "History messages should be received"
        1 * getMessagesCallback.receive(wrap(history))

        when: "Receiving chat with history"
        messageHolder.onChatReceive(null, chat, currentChat)
        then: "Changed message callback shouldn't be called"
        0 * messageListener.messageChanged(wrap(history[0]), wrap(currentChat[0]))

        when: "Deleting current chat message"
        messageHolder.onMessageDeleted(currentChat[0].getIdInCurrentChat())
        then: "Deleted message callback should be called"
        1 * messageListener.messageRemoved(_)

        when: "Receiving chat again"
        messageHolder.onChatReceive(chat, chat, currentChat)
        then: "Added message callback should be called"
        1 * messageListener.messageAdded(null, wrap(currentChat[0]))
    }

}
