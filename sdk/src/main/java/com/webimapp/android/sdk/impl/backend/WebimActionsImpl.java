package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.impl.WebimErrorImpl;
import com.webimapp.android.sdk.impl.items.responses.DefaultResponse;
import com.webimapp.android.sdk.impl.items.responses.HistoryBeforeResponse;
import com.webimapp.android.sdk.impl.items.responses.HistorySinceResponse;
import com.webimapp.android.sdk.impl.items.responses.UploadResponse;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;

public class WebimActionsImpl implements WebimActions {
    private static final MediaType PLAIN_TEXT = MediaType.parse("text/plain");
    private static final RequestBody CHAT_MODE_ONLINE = RequestBody.create(PLAIN_TEXT, "online");
    private static final String ACTION_CHAT_CLOSE = "chat.close";
    private static final String ACTION_CHAT_READ_BY_VISITOR = "chat.read_by_visitor";
    private static final String ACTION_CHAT_START = "chat.start";
    private static final String ACTION_CHAT_MESSAGE = "chat.message";
    private static final String ACTION_CHAT_DELETE_MESSAGE = "chat.delete_message";
    private static final String ACTION_OPERATOR_RATE = "chat.operator_rate_select";
    private static final String ACTION_PUSH_TOKEN_SET = "set_push_token";
    private static final String ACTION_REQUEST_CALL_SENTRY
            = "chat.action_request.call_sentry_action_request";
    private static final String ACTION_SET_PRECHAT_FIELDS = "chat.set_prechat_fields";
    private static final String ACTION_VISITOR_TYPING = "chat.visitor_typing";
    private static final String ACTION_WIDGET_UPDATE = "widget.update";
    private static final String CHARACTERS_TO_ENCODE = "\n!#$&'()*+,/:;=?@[] \"%-.<>\\^_`{|}~";
    private static final String ACTION_CHAT_KEYBOARD_RESPONSE = "chat.keyboard_response";
    @NonNull
    private final ActionRequestLoop requestLoop;
    @NonNull
    private final WebimService webim;

    WebimActionsImpl(@NonNull WebimService webim, @NonNull ActionRequestLoop requestLoop) {
        this.webim = webim;
        this.requestLoop = requestLoop;
    }

    private void enqueue(ActionRequestLoop.WebimRequest<?> request) {
        requestLoop.enqueue(request);
    }

    @Override
    public void sendMessage(@NonNull final String message,
                            @NonNull final String clientSideId,
                            @Nullable final String dataJsonString,
                            final boolean isHintQuestion,
                            @Nullable final SendOrDeleteMessageInternalCallback callback) {
        message.getClass(); // NPE
        clientSideId.getClass(); // NPE

        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>((callback != null)) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                /*
                Custom percent encoding for message because Retrofit/OkHTTP don't encode
                semicolons.
                */
                return webim.sendMessage(
                        ACTION_CHAT_MESSAGE,
                        percentEncode(message),
                        clientSideId,
                        authData.getPageId(),
                        authData.getAuthToken(),
                        (isHintQuestion ? true : null),
                        dataJsonString);
            }

            @Override
            public void runCallback(DefaultResponse response) {
                //noinspection ConstantConditions
                callback.onSuccess();
            }

            @Override
            public boolean isHandleError(@NonNull String error) {
                return true;
            }

            @Override
            public void handleError(@NonNull String error) {
                //noinspection ConstantConditions
                callback.onFailure(error);
            }
        });
    }

    @Override
    public void sendKeyboard(@NonNull final String requestMessageId,
                             @NonNull final String buttonId,
                             @Nullable final SendKeyboardErrorListener callback) {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(callback != null) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.sendKeyboardResponse(
                        authData.getPageId(),
                        authData.getAuthToken(),
                        ACTION_CHAT_KEYBOARD_RESPONSE,
                        requestMessageId,
                        buttonId);
            }

            @Override
            public void runCallback(DefaultResponse response) {
                //noinspection ConstantConditions
                callback.onSuccess();
            }

            @Override
            public boolean isHandleError(@NonNull String error) {
                return true;
            }

            @Override
            public void handleError(@NonNull String error) {
                //noinspection ConstantConditions
                callback.onFailure(error);
            }
        });
    }

    @Override
    public void deleteMessage(@NonNull final String clientSideId,
                              @Nullable final SendOrDeleteMessageInternalCallback callback) {
        clientSideId.getClass(); // NPE

        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(callback != null) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.deleteMessage(
                        ACTION_CHAT_DELETE_MESSAGE,
                        clientSideId,
                        authData.getPageId(),
                        authData.getAuthToken());
            }

            @Override
            public void runCallback(DefaultResponse response) {
                //noinspection ConstantConditions
                callback.onSuccess();
            }

            @Override
            public boolean isHandleError(@NonNull String error) {
                return true;
            }

            @Override
            public void handleError(@NonNull String error) {
                //noinspection ConstantConditions
                callback.onFailure(error);
            }
        });
    }

    @Override
    public void sendFile(final @NonNull RequestBody body,
                         final @NonNull String filename,
                         final @NonNull String clientSideId,
                         final @Nullable SendOrDeleteMessageInternalCallback callback) {
        body.getClass(); // NPE
        filename.getClass(); // NPE
        clientSideId.getClass(); // NPE

        enqueue(new ActionRequestLoop.WebimRequest<UploadResponse>((callback != null)) {
            @Override
            public Call<UploadResponse> makeRequest(AuthData authData) {
                return webim.uploadFile(
                        MultipartBody.Part.createFormData(
                                "webim_upload_file",
                                filename,
                                body),
                        CHAT_MODE_ONLINE,
                        RequestBody.create(PLAIN_TEXT, clientSideId),
                        RequestBody.create(PLAIN_TEXT, authData.getPageId()),
                        ((authData.getAuthToken() == null)
                                ? null
                                : RequestBody.create(PLAIN_TEXT, authData.getAuthToken()))
                );
            }

            @Override
            public void runCallback(UploadResponse response) {
                //noinspection ConstantConditions
                callback.onSuccess();
            }

            @Override
            public boolean isHandleError(@NonNull String error) {
                return (error.equals(WebimInternalError.FILE_TYPE_NOT_ALLOWED)
                        || error.equals(WebimInternalError.FILE_SIZE_EXCEEDED)
                        || error.equals(WebimInternalError.UPLOADED_FILE_NOT_FOUND));
            }

            @Override
            public void handleError(@NonNull String error) {
                //noinspection ConstantConditions
                callback.onFailure(error);
            }
        });
    }

    @Override
    public void closeChat() {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.closeChat(
                        ACTION_CHAT_CLOSE,
                        authData.getPageId(),
                        authData.getAuthToken());
            }
        });
    }

    @Override
    public void startChat(@NonNull final String clientSideId,
                          @Nullable final String departmentKey,
                          @Nullable final String firstQuestion,
                          @Nullable final String customFields) {
        clientSideId.getClass(); // NPE

        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.startChat(
                        ACTION_CHAT_START,
                        true,
                        clientSideId,
                        authData.getPageId(),
                        authData.getAuthToken(),
                        departmentKey,
                        firstQuestion,
                        customFields
                );
            }
        });
    }

    @Override
    public void setChatRead() {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.setChatRead(
                        ACTION_CHAT_READ_BY_VISITOR,
                        authData.getPageId(),
                        authData.getAuthToken()
                );
            }
        });
    }

    @Override
    public void setPrechatFields(@NonNull final String prechatFields) {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.setPrechatFields(
                        ACTION_SET_PRECHAT_FIELDS,
                        prechatFields,
                        authData.getPageId(),
                        authData.getAuthToken());
            }
        });
    }

    @Override
    public void setVisitorTyping(final boolean typing,
                                 final @Nullable String draftMessage,
                                 final boolean deleteDraft) {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.setVisitorTyping(
                        ACTION_VISITOR_TYPING,
                        typing,
                        draftMessage,
                        deleteDraft,
                        authData.getPageId(),
                        authData.getAuthToken());
            }
        });
    }

    public void updatePushToken(@NonNull final String pushToken) {
        pushToken.getClass(); // NPE
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.updatePushToken(
                        ACTION_PUSH_TOKEN_SET,
                        pushToken,
                        authData.getPageId(),
                        authData.getAuthToken());
            }
        });
    }

    @Override
    public void rateOperator
            (@Nullable final String operatorId,
             final int rate,
             @Nullable final MessageStream.RateOperatorCallback rateOperatorCallback) {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(
                (rateOperatorCallback != null)
        ) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.rateOperator(
                        ACTION_OPERATOR_RATE,
                        operatorId,
                        rate,
                        authData.getPageId(),
                        authData.getAuthToken());
            }

            @Override
            public void runCallback(DefaultResponse response) {
                if (rateOperatorCallback != null) {
                    rateOperatorCallback.onSuccess();
                }
            }

            @Override
            public boolean isHandleError(@NonNull String error) {
                return (error.equals(WebimInternalError.OPERATOR_NOT_IN_CHAT)
                        || error.equals(WebimInternalError.NO_CHAT));
            }

            @Override
            public void handleError(@NonNull String error) {
                if (rateOperatorCallback != null) {
                    rateOperatorCallback.onFailure((new WebimErrorImpl<>
                            (error.equals(WebimInternalError.NO_CHAT)
                                    ? MessageStream.RateOperatorCallback.RateOperatorError
                                    .NO_CHAT
                                    : MessageStream.RateOperatorCallback.RateOperatorError
                                    .OPERATOR_NOT_IN_CHAT,
                                    error)));
                }
            }
        });
    }

    @Override
    public void respondSentryCall(@NonNull final String clientSideId) {
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.respondSentryCall(
                        ACTION_REQUEST_CALL_SENTRY,
                        authData.getPageId(),
                        authData.getAuthToken(),
                        clientSideId);
            }
        });
    }

    @Override
    public void requestHistoryBefore
            (final long beforeTs,
             @NonNull final DefaultCallback<HistoryBeforeResponse> callback) {
        callback.getClass(); // NPE
        enqueue(new ActionRequestLoop.WebimRequest<HistoryBeforeResponse>(true) {
            @Override
            public Call<HistoryBeforeResponse> makeRequest(AuthData authData) {
                return webim.getHistoryBefore(authData.getPageId(),
                        authData.getAuthToken(), beforeTs);
            }

            @Override
            public void runCallback(HistoryBeforeResponse response) {
                callback.onSuccess(response);
            }
        });
    }

    @Override
    public void requestHistorySince(final @Nullable String since,
                                    @NonNull final DefaultCallback<HistorySinceResponse> callback) {
        callback.getClass(); // NPE
        enqueue(new ActionRequestLoop.WebimRequest<HistorySinceResponse>(true) {
            @Override
            public Call<HistorySinceResponse> makeRequest(AuthData authData) {
                return webim.getHistorySince(authData.getPageId(), authData.getAuthToken(), since);
            }

            @Override
            public void runCallback(HistorySinceResponse response) {
                callback.onSuccess(response);
            }
        });
    }

    @Override
    public void updateWidgetStatus(@NonNull final String data) {
        data.getClass(); //NPE
        enqueue(new ActionRequestLoop.WebimRequest<DefaultResponse>(false) {
            @Override
            public Call<DefaultResponse> makeRequest(AuthData authData) {
                return webim.updateWidgetStatus(
                        ACTION_WIDGET_UPDATE,
                        data,
                        authData.getPageId(),
                        authData.getAuthToken()
                );
            }
        });
    }

    private static String percentEncode(String input) {
        if ((input == null) || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder(input);
        for (int i = (input.length() - 1); i >= 0; i--) {
            if (CHARACTERS_TO_ENCODE.indexOf(input.charAt(i)) != -1) {
                result.replace(
                        i,
                        (i + 1),
                        ("%" + Integer.toHexString(0x100 | input.charAt(i))
                                .substring(1).toUpperCase())
                );
            }
        }

        return result.toString();
    }
}
