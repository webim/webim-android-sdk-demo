package com.webimapp.android.sdk;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

/**
 * Abstracts a single message in the message history. A message is an immutable object. It means that for changing 
 * some of the message fields there will be created a new object. That is why messages can be compared by using ({@code equals}) for searching messages
 * with the same set of fields or by id ({@code msg1.getId().equals(msg2.getId())}) for searching logically identical messages. Id is formed 
 * on the client side when sending a message ({@link MessageStream#sendMessage} or {@link MessageStream#sendFile}).
 */
public interface Message {
    /**
	 * @return unique id of the message. Notice that id doesn’t change while changing the content of a message.
     */
    @NonNull Id getId();

    /**
     * @return current chat id of the message.
     */
    @NonNull String getCurrentChatId();

    /**
     * @return id of a sender, if the sender is an operator
     */
    @Nullable Operator.Id getOperatorId();

    /**
     * @return URL of a sender's avatar
     */
    @Nullable String getSenderAvatarUrl();

    /**
     * @return name of a message sender
     */
    @NonNull String getSenderName();

    /**
     * @return type of a message
     */
    @NonNull Type getType();

    /**
     * @return epoch time (in milliseconds) the message was processed by the server
     */
    long getTime();

    /**
     * @return text of the message
     */
    @NonNull String getText();

    /**
	 * Messages of the types {@link Type#FILE_FROM_OPERATOR} and {@link Type#FILE_FROM_VISITOR} can contain attachments.
     * Notice that this method may return null even in the case of previously listed types of messages. For instance,
     * if a file is being sent.
     * @return the attachment of the message
     */
    @Nullable Attachment getAttachment();

    /**
     * @return {@link SendStatus#SENT} if a message had been sent to the server, was received by the server and was 
	 * delivered to all the clients;
     * {@link SendStatus#SENDING} if not
     */
    @NonNull SendStatus getSendStatus();

    /**
     * @return String formatted using JSON if message contains some custom fields
     * of {@link Type#ACTION_REQUEST} Message type.
     * This data is to be unparsed by a client.
     */
    @Nullable
    String getData();

    /**
     * @return true if this visitor message is read by operator or this message is not by visitor.
     */
    boolean isReadByOperator();

    /**
     * @return true if this message can be edited.
     */
    boolean canBeEdited();

    /**
     * Abstracts unique id of the message. The class was designed only to be compared by {@code equals}.
     * @see Message#getId()
     */
    interface Id {
    }

    enum Type {
        /**
         * A message from operator which requests some actions from a visitor.
         * E.g. choose an operator group by clicking on a button in this message.
         * @see Message#getData()
         */
        ACTION_REQUEST,
        /**
         * A message from operator which requests some information about a visitor.
         * @see Message#getData()
         */
        CONTACT_REQUEST,
        /**
         * A message sent by an operator which contains an attachment.
         * Notice that the method {@link Message#getAttachment()} may  return null even for messages of this type. For instance,
         * if a file is being sent.
         * @see Message#getAttachment()
         */
        FILE_FROM_OPERATOR,
        /**
         * A message sent by a visitor which contains an attachment.
         * Notice that the method {@link Message#getAttachment()} may return null even for messages of this type. For instance,
         * if a file is being sent.
         * @see Message#getAttachment()
         */
        FILE_FROM_VISITOR,
        /**
         * A system information message. Messages of this type are automatically sent at specific events.
         * For example when starting a chat, closing a chat or when an operator joins a chat.
		 */
        INFO,
        /**
         * The system message that the chat bot sends.
         */
        KEYBOARD,
        /**
         * A system message from the chat bot containing information about the button that the user selected.
         */
        KEYBOARD_RESPONCE,
        /**
         * A text message sent by an operator.
         */
        OPERATOR,
        /**
         * A system information message which indicates that an operator is busy and can not reply at the moment.
         */
        OPERATOR_BUSY,
        /**
         * A text message sent by a visitor.
         */
        VISITOR
    }

    /**
     * Until a message will be sent to the server, will have been recieved by the server and will have been spreaded among clients, we can
	 * see the message as "being sent", at the same time {@link Message#getSendStatus()} will return {@link SendStatus#SENDING}. 
	 * In other cases - {@link SendStatus#SENT}
	 */
    enum SendStatus {
        /**
         * A message is being sent.
         */
        SENDING,
        /**
         * A message had been sent to the server, recieved by the server and was spreaded among clients.
         */
        SENT
    }

    /**
     * Contains information about an attachment file.
     * @see Message#getAttachment()
     */
    interface Attachment {
        /**
         * A URL of a file. 
		 * Notice that this URL is short-lived and is tied to a session.
         * @return url of the file
         */
        @NonNull String getUrl();

        /**
         * @return file size in bytes
         */
        long getSize();

        /**
		 * @return name of a file
         */
        @NonNull String getFileName();

        /**
         * @return MIME-type of a file
         */
        @NonNull String getContentType();

        /**
         * @return if a file is an image, returns information about an image, in other cases returns null
         */
        @Nullable ImageInfo getImageInfo();
    }

    /**
     * Contains information about an image.
     * @see Attachment#getImageInfo()
     */
    interface ImageInfo {
        /**
         * Returns a URL of a thumbnail. The maximum width and height is usually 300 pixels but it can be adjusted at server settings.
		 * To get an actual preview size before file uploading will be completed, use the following code:
		 * <code><pre>
         *     THUMB_SIZE = 300;
         *     int width = imageInfo.getWidth();
         *     int height = imageInfo.getHeight();
         *     if(height > width) {
         *         width = THUMB_SIZE * width / height;
         *         height = THUMB_SIZE;
         *     } else {
         *         height = THUMB_SIZE * height / width;
         *         width = THUMB_SIZE;
         *     }
         * </pre></code>
		 * Notice that this URL is short-lived and is tied to a session.
         * @return URL уменьшенной копии изображении
         */
        @NonNull String getThumbUrl();

        /**
         * @return width of an image
         */
        int getWidth();

        /**
         * @return height of an image
         */
        int getHeight();
    }

    /**
     * @return keyboard item for chat bot
     */
    @Nullable
    Keyboard getKeyboard();

    /**
     * @return the button that was pressed in keyboard item
     */
    @Nullable
    KeyboardRequest getKeyboardRequest();

    /**
     * Contains information about keyboard item.
     * @see Message#getKeyboard()
     */
    interface Keyboard {
        /**
         * @return List of buttons from keyboard item
         */
        @Nullable List<List<KeyboardButtons>> getButtons();

        /**
         * @return state from keyboard item
         */
        @Nullable String getState();

        /**
         * @return the button that was pressed in keyboard item
         */
        @Nullable
        KeyboardResponse getKeyboardResponse();
    }

    /**
     * Contains information about buttons in keyboard item.
     * @see Keyboard#getButtons()
     */
    interface KeyboardButtons {
        /**
         * @return the id of the button in the keyboard item that will be shown in the chat
         */
        @NonNull String getId();

        /**
         * @return the text of the button in the keyboard item that will be shown in the chat
         */
        @NonNull String getText();
    }

    /**
     * Contains information about the pressed button in keyboard item.
     * @see Keyboard#getKeyboardResponse()
     */
    interface KeyboardResponse {
        /**
         * @return the button id that was pressed in keyboard item
         */
        @NonNull String getButtonId();

        /**
         * @return the button text that was pressed in keyboard item
         */
        @NonNull String getMessageId();
    }

    /**
     * Contains information about the pressed button in keyboard item.
     * @see Message#getKeyboardRequest()
     */
    interface KeyboardRequest {
        /**
         * @return the button that was pressed in keyboard item
         */
        @Nullable
        KeyboardButtons getButtons();

        /**
         * @return the message id that was pressed in keyboard item
         */
        @NonNull String getMessageId();
    }
}
