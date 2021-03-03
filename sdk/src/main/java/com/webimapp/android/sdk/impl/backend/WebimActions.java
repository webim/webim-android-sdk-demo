package com.webimapp.android.sdk.impl.backend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.impl.items.responses.HistoryBeforeResponse;
import com.webimapp.android.sdk.impl.items.responses.HistorySinceResponse;
import com.webimapp.android.sdk.impl.items.responses.LocationStatusResponse;
import com.webimapp.android.sdk.impl.items.responses.SearchResponse;

import okhttp3.RequestBody;

public interface WebimActions {

    void sendMessage(@NonNull String message,
                     @NonNull String clientSideId,
                     @Nullable String dataJsonString,
                     boolean isHintQuestion,
                     @Nullable SendOrDeleteMessageInternalCallback callback);

    void sendFiles(@NonNull String message,
                   @NonNull String clientSideId,
                   boolean isHintQuestion,
                   @Nullable SendOrDeleteMessageInternalCallback callback);

    void sendKeyboard(@NonNull String requestMessageId,
                      @NonNull String buttonId,
                      @Nullable SendKeyboardErrorListener callback);

    void deleteMessage(@NonNull String clientSideId,
                       @Nullable SendOrDeleteMessageInternalCallback callback);

    void replyMessage(@NonNull String message,
                      @NonNull String clientSideId,
                      @NonNull String quoteMessageId);

    void sendFile(@NonNull RequestBody body,
                  @NonNull String filename,
                  @NonNull String clientSideId,
                  @Nullable SendOrDeleteMessageInternalCallback callback);

    void deleteUploadedFile(@NonNull String fileGuid,
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

    void searchMessages(@NonNull String query,
                        @NonNull DefaultCallback<SearchResponse> callback);

    void rateOperator(@Nullable String operatorId,
                      @Nullable String note,
                      int rate,
                      @Nullable MessageStream.RateOperatorCallback rateOperatorCallback);

    void respondSentryCall(@NonNull String clientSideId);

    void requestHistoryBefore(long beforeMessageTs,
                              @NonNull DefaultCallback<HistoryBeforeResponse> callback);

    void requestHistorySince(@Nullable String since,
                             @NonNull DefaultCallback<HistorySinceResponse> callback);

    void updateWidgetStatus(@NonNull String data);

    void sendChatToEmailAddress(@NonNull String email,
                                @NonNull MessageStream.SendDialogToEmailAddressCallback sendChatToEmailCallback);

    void sendSticker(int stickerId,
                     @NonNull String clientSideId,
                     @Nullable MessageStream.SendStickerCallback callback);

    void sendQuestionAnswer(@NonNull String surveyId,
                            int formId,
                            int questionId,
                            @NonNull String surveyAnswer,
                            @Nullable SurveyQuestionCallback callback);

    void closeSurvey(@NonNull String surveyId, @NonNull SurveyFinishCallback callback);

    void getLocationStatus(@NonNull String location, @NonNull DefaultCallback<LocationStatusResponse> callback);
}
