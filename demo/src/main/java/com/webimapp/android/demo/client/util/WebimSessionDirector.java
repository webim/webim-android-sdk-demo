package com.webimapp.android.demo.client.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.webimapp.android.demo.client.BuildConfig;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimLog;

public class WebimSessionDirector {

    private static Webim.SessionBuilder newSessionBuilder(Context context) {
        String DEFAULT_ACCOUNT_NAME = "demo";
        String DEFAULT_LOCATION = "mobile";
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return Webim.newSessionBuilder()
                .setContext(context)
                .setAccountName(sharedPref.getString("account", DEFAULT_ACCOUNT_NAME))
                .setLocation(sharedPref.getString("location", DEFAULT_LOCATION))
                .setPushSystem(Webim.PushSystem.FCM)
                .setPushToken(sharedPref.getBoolean("fcm", true)
                        ? FirebaseInstanceId.getInstance().getToken()
                        : "none")
                .setLogger(BuildConfig.DEBUG
                                ? new WebimLog() {
                            @Override
                            public void log(String log) {
                                Log.i("WEBIM LOG", log);
                            }
                        }
                                : null,
                        Webim.SessionBuilder.WebimLogVerbosityLevel.VERBOSE);
//                    .setVisitorDataPreferences(context.getSharedPreferences("test2", Context.MODE_PRIVATE));
    }

    public static Webim.SessionBuilder createSessionBuilderWithAnonymousVisitor(Context context) {
        return newSessionBuilder(context);
    }

    public static Webim.SessionBuilder createSessionBuilderWithAuth1Visitor(Context context) {
        String visitorFieldAuthVersion1 =
                "{\"id\":\"1234567890987654321\"," +
                        "\"display_name\":\"Никита\"," +
                        "\"crc\":\"ffadeb6aa3c788200824e311b9aa44cb\"}";
        return newSessionBuilder(context).setVisitorFieldsJson(visitorFieldAuthVersion1);
    }

    public static Webim.SessionBuilder createSessionBuilderWithAuth2Visitor(Context context) {
        String visitorFieldAuthVersion2 =
                "{\"fields\":{" +
                        "\"display_name\": \"Fedor\"," +
                        "\"email\": \"fedor@webim.ru\"," +
                        "\"phone\": \"123-3243\"," +
                        "\"id\": \"2113\"," +
                        "\"comment\": \"some\\ncomment\"," +
                        "\"info\": \"some\\ninfo\"," +
                        "\"icq\": \"12345678\"," +
                        "\"profile_url\": \"http:\\\\\\\\some-crm.ru\\\\id12345\"}," +
                        "\"expires\": 1605109076," +
                        "\"hash\": \"87233cd41af79e3736e73bfb12b803fb\"}";
        return newSessionBuilder(context).setVisitorFieldsJson(visitorFieldAuthVersion2);
    }
}
