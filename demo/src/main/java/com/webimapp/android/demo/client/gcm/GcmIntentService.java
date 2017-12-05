package com.webimapp.android.demo.client.gcm;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.webimapp.android.demo.client.R;
import com.webimapp.android.demo.client.WebimChatActivity;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimPushNotification;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
    private static final int NOTIFICATION_ID = "Webim".hashCode(); // Change to any number you want

    public GcmIntentService() {
        super("GCM push handler");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        String senderId = intent.getStringExtra("from");
        if (senderId.equals(Webim.getGcmSenderId())) {
            if (!WebimChatActivity.isActive()) {
                onPushMessage(getApplicationContext(),
                        Webim.parseGcmPushNotification(extras), WebimChatActivity.class);
            }
        } else {
            // Your push logic
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private static void onPushMessage(Context context,
                                      @Nullable WebimPushNotification push,
                                      Class<? extends Activity> clazz) {
        if (push == null) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String event = push.getEvent();
        if (event.equals("add")) {
            String message = getMessageFromPush(context, push);
            if (message == null) {
                return;
            }
            generateNotification(context, message, notificationManager, clazz);
        } else if (event.equals("del")) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private static @Nullable
    String getMessageFromPush(@NonNull Context context, @NonNull WebimPushNotification push) {
        try {
            String format;
            switch (push.getType()) {
                case OPERATOR_ACCEPTED:
                    format = context.getResources().getString(R.string.push_operator_accepted);
                    break;
                case OPERATOR_MESSAGE:
                    format = context.getResources().getString(R.string.push_operator_message);
                    break;
                case OPERATOR_FILE:
                    format = context.getResources().getString(R.string.push_operator_file);
                    break;
                default:
                    return null;
            }

            return String.format(format, push.getParams().toArray());
        } catch (Exception ignore) {
        }

        return null;
    }

    private static void generateNotification(Context context,
                                             String message,
                                             NotificationManager notificationManager,
                                             Class<? extends Activity> cls) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Resources res = context.getResources();
        if (cls != null) {
            Intent notificationIntent = new Intent(context, cls);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context,
                    0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(contentIntent);
        }
        builder.setSmallIcon(R.drawable.ic_notify)
                .setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.ic_big_notify))
                .setTicker(res.getString(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(res.getString(R.string.app_name))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setSound(Uri.parse("android.resource://"
                        + context.getPackageName() + "/" + R.raw.newmessageoperator));
        Notification notification = builder.build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}