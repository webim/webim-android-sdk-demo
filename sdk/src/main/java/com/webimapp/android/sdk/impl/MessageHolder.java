package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.impl.items.ChatItem;

import java.util.List;
import java.util.Set;

public interface MessageHolder {
    MessageTracker newMessageTracker(@NonNull MessageListener messageListener);

    void receiveHistoryUpdate(List<? extends MessageImpl> messages,
                              Set<String> deleted,
                              Runnable callback);

    void setReachedEndOfRemoteHistory(boolean reachedEndOfHistory);

    void onChatReceive(@Nullable ChatItem oldChat,
                       @Nullable ChatItem newChat,
                       @CurrentChat List<? extends MessageImpl> newMessages);

    void receiveNewMessage(@NonNull @CurrentChat MessageImpl msg);

    void onMessageChanged(@NonNull @CurrentChat MessageImpl newMessage);

    void onMessageDeleted(@NonNull String idInCurrentChat);

    void onSendingMessage(@NonNull MessageSending message);

    @Nullable
    String onChangingMessage(@NonNull Message.Id id, @Nullable String text);

    void onMessageSendingCancelled(@NonNull Message.Id id);

    void onMessageChangingCancelled(@NonNull Message.Id id, @NonNull String text);

    void updateReadBeforeTimestamp(Long timestamp);
}
