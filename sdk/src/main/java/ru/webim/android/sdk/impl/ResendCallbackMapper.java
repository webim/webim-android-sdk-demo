package ru.webim.android.sdk.impl;

import androidx.annotation.NonNull;

import ru.webim.android.sdk.Message;
import ru.webim.android.sdk.MessageStream;
import ru.webim.android.sdk.WebimError;

class ResendCallbackMapper {
    MessageStream.SendFilesCallback mapFilesSendCallback(MessageStream.ResendMessageCallback callback) {
        return new MessageStream.SendFilesCallback() {
            @Override
            public void onSuccess(@NonNull Message.Id messageId) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(@NonNull Message.Id messageId, @NonNull WebimError<MessageStream.SendFileCallback.SendFileError> sendFilesError) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        };
    }

    MessageStream.SendFileCallback mapFileSendCallback(MessageStream.ResendMessageCallback callback) {
        return new MessageStream.SendFileCallback() {
            @Override
            public void onProgress(@NonNull Message.Id id, long sentBytes) {
            }

            @Override
            public void onSuccess(@NonNull Message.Id id) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(@NonNull Message.Id id, @NonNull WebimError<SendFileError> error) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        };
    }

    MessageStream.SendStickerCallback mapStickerSendCallback(MessageStream.ResendMessageCallback callback) {
        return new MessageStream.SendStickerCallback() {
            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(@NonNull WebimError<SendStickerError> sendStickerErrorWebimError) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        };
    }

    MessageStream.DataMessageCallback mapMessageSendCallback(MessageStream.ResendMessageCallback callback) {
        return new MessageStream.DataMessageCallback() {
            @Override
            public void onSuccess(@NonNull Message.Id messageId) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(@NonNull Message.Id messageId, @NonNull WebimError<DataMessageError> dataMessageError) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        };
    }
}


