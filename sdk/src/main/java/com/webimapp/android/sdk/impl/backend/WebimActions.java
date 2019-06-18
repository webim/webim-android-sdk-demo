package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.impl.items.responses.HistoryBeforeResponse;
import com.webimapp.android.sdk.impl.items.responses.HistorySinceResponse;

import okhttp3.RequestBody;

public interface WebimActions {

    void sendMessage(@NonNull String message,
                     @NonNull String clientSideId,
                     @Nullable String dataJsonString,
                     boolean isHintQuestion,
                     @Nullable SendOrDeleteMessageInternalCallback callback);

    void sendKeyboard(@NonNull String requestMessageId,
                      @NonNull String buttonId,
                      @Nullable SendKeyboardErrorListener callback);

    void deleteMessage(@NonNull String clientSideId,
                       @Nullable SendOrDeleteMessageInternalCallback callback);

    void sendFile(@NonNull RequestBody body,
                  @NonNull String filename,
                  @NonNull String clientSideId,
                  @Nullable SendOrDeleteMessageInternalCallback callback);

    void closeChat();

    void startChat(@NonNull String clientSideId,
                   @Nullable String departmentKey,
                   @Nullable String firstQuestion,
                   @Nullable String customFields);

    void setChatRead();

    void setPrechatFields(@NonNull String prechatFields);

    void setVisitorTyping(boolean typing,
                          @Nullable String draftMessage,
                          boolean deleteDraft);

    void rateOperator(@Nullable String operatorId,
                      int rate,
                      @Nullable MessageStream.RateOperatorCallback rateOperatorCallback);

    void respondSentryCall(@NonNull String clientSideId);

    void requestHistoryBefore(long beforeMessageTs,
                              @NonNull DefaultCallback<HistoryBeforeResponse> callback);

    void requestHistorySince(@Nullable String since,
                             @NonNull DefaultCallback<HistorySinceResponse> callback);

    void updateWidgetStatus(@NonNull String data);
}
