package com.webimapp.android.sdk;

import android.support.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Abstracts a push notification
 * @see Webim#parseFcmPushNotification(String)
 */
public interface WebimPushNotification {
    /**
     * @return the type of the notification
     */
    @NonNull NotificationType getType();

    /**
     * This method may return of two values:
     * <ul>
     *     <li>"add" - means that a notification should be added by this push</li>
     *     <li>"del" - means that a notification should be deleted by this push</li>
     * </ul>
     * @return the event of this notification
     */
    @NonNull String getEvent();

    /**
     * @return parameters of this notification. Each {@link NotificationType} has specific list of parameters
     * @see NotificationType
     */
    @NonNull List<String> getParams();

    /**
     *
     * @see WebimPushNotification#getType()
     */
    enum NotificationType {
        /**
         * This notification type indicated that contact information request is sent to a visitor.
         * Parameters: empty.
         */
        @SerializedName("P.CR")
        CONTACT_INFORMATION_REQUEST,

        /**
         * This notification type indicated that an operator has connected to a dialogue.
         * Parameters:
         * <ul>
         *     <li>operator's name</li>
         * </ul>
         */
        @SerializedName("P.OA")
        OPERATOR_ACCEPTED,

        /**
         * This notification type indicated that an operator has sent a file.
         * Parameters:
         * <ul>
         *     <li>Operator's name</li>
         *     <li>name of a file</li>
         * </ul>
         */
        @SerializedName("P.OF")
        OPERATOR_FILE,

        /**
         * This notification type indicated that an operator has sent a text message.
         * Parameters:
         * <ul>
         *     <li>Operator's name</li>
         *     <li>Text</li>
         * </ul>
         */
        @SerializedName("P.OM")
        OPERATOR_MESSAGE,

        /**
         * This notification type indicated that an operator has sent a widget message.
         * This type can be received only if server supports this functionality.
         * Parameters: empty.
         */
        @SerializedName("P.WM")
        WIDGET
    }
}
