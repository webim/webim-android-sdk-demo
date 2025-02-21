package ru.webim.android.demo.pushes.huawei;

import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import ru.webim.android.demo.pushes.WebimPushSender;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.impl.backend.WebimInternalLog;

public class WebimHuaweiMessagingService extends HmsMessageService {
    private static final String TAG = "PushDemoLog";

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling, the server can return the token through this method later.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     *
     * @param token token
     */
    @Override
    public void onNewToken(String token) {
    }

    @Override
    public void onMessageDelivered(String s, Exception e) {
        super.onMessageDelivered(s, e);
    }

    /**
     * This method is used to receive downstream data messages.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     *
     * @param message RemoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (message == null) {
            Log.e(TAG, "Received message entity is null!");
            return;
        }

        String pushPayload = null;
        try {
            JSONObject jsonObject = new JSONObject(message.getData());
            JSONObject pushBody = jsonObject.getJSONObject("pushbody");
            pushPayload = pushBody.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            WebimInternalLog.getInstance().log("Push error: " + e.getMessage(), Webim.SessionBuilder.WebimLogVerbosityLevel.ERROR);

        }
        if (pushPayload != null) {
            WebimPushSender.getInstance().onPushMessage(
                getApplicationContext(),
                Webim.parseFcmPushNotification(pushPayload)
            );
        }
    }

    @Override
    public void onMessageSent(String msgId) {
    }

    @Override
    public void onSendError(String msgId, Exception exception) {
    }

    @Override
    public void onTokenError(Exception e) {
        super.onTokenError(e);
    }
}
