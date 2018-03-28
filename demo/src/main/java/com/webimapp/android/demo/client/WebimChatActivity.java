package com.webimapp.android.demo.client;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.webimapp.android.sdk.FatalErrorHandler;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimError;
import com.webimapp.android.sdk.WebimLog;

public class WebimChatActivity extends AppCompatActivity implements FatalErrorHandler {
    private static boolean active;
    public static final String DEFAULT_ACCOUNT_NAME = "demo";
    public static final String DEFAULT_LOCATION = "mobile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // Prevents fragment recovery (we need to set session)
        setContentView(R.layout.activity_webim_chat);
        updateBackground(getResources().getConfiguration());

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        WebimChatFragment fragment = new WebimChatFragment();
        fragment.setWebimSession(Webim.newSessionBuilder()
                .setContext(this)
                .setErrorHandler(this)
                .setAccountName(sharedPref.getString("account", DEFAULT_ACCOUNT_NAME))
                .setLocation(sharedPref.getString("location", DEFAULT_LOCATION))
                .setPushSystem(Webim.PushSystem.FCM)
                .setPushToken(sharedPref.getBoolean("fcm", true)
                        ? FirebaseInstanceId.getInstance().getToken()
                        : "none")
                .setClearVisitorData(false)
                .setLogger(BuildConfig.DEBUG
                        ? new WebimLog() {
                                @Override
                                public void log(String log) {
                                    Log.i("WEBIM LOG", log);
                                }
                        }
                        : null,
                        Webim.SessionBuilder.WebimLogVerbosityLevel.VERBOSE)
                //.setVisitorFieldsJson("{\"id\":\"1234567890987654321\",\"display_name\":\"Никита\",\"crc\":\"ffadeb6aa3c788200824e311b9aa44cb\"}")
                //.setVisitorDataPreferences(getSharedPreferences("test2", Context.MODE_PRIVATE))
                .build());

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.webimChatContainer, fragment)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateBackground(newConfig);
        super.onConfigurationChanged(newConfig);
    }

    private void updateBackground(Configuration newConfig) {
        View v = findViewById(R.id.webimChatContainer);
        if (v != null) {
            v.setBackgroundResource(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? R.drawable.bg_webim_scaled_land
                    : R.drawable.bg_webim_scaled);
        }
    }

    @Override
    public void onError(@NonNull WebimError<FatalErrorType> error) {
        switch (error.getErrorType()) {
            default:
                if (!BuildConfig.DEBUG) {
                    Crashlytics.logException(new Throwable("Handled unknown webim error: "
                            + error.getErrorString()));
                }
                showError(R.string.error_unknown_header,
                        R.string.error_unknown_desc, error.getErrorString());
                break;
            case ACCOUNT_BLOCKED:
                showError(R.string.error_account_blocked_header,
                        R.string.error_account_blocked_desc);
                break;
            case VISITOR_BANNED:
                showError(R.string.error_user_banned_header, R.string.error_user_banned_desc);
                break;
        }
    }

    private void showError(int errorHeaderId, int errorDescId, String... args) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.webimChatContainer,
                        ErrorFragment.newInstance(errorHeaderId, errorDescId, args))
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true; // It's singleTask activity
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
