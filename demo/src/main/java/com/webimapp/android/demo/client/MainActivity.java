package com.webimapp.android.demo.client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.Webim;
import com.webimapp.android.sdk.WebimLog;
import com.webimapp.android.sdk.WebimSession;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity {

    private WebimSession session;
    private ProgressBar progressBar;
    private TextView numberOfBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            Fabric.with(this, new Crashlytics());
        }
        setContentView(R.layout.activity_main);
        initNewChatButton();
        initViewForBagde();
        initSettingButton();
        initSession();
    }

    private void initNewChatButton() {
        Button newChartButtom = findViewById(R.id.buttonStartChat);
        newChartButtom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, WebimChatActivity.class));
                overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
            }
        });
    }

    private void initViewForBagde() {
        progressBar = findViewById(R.id.progressBar);
        numberOfBadge = findViewById(R.id.textNumberOfBadge);
    }

    private void initSettingButton() {
        Button settingsButton = findViewById(R.id.buttonCettings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
            }
        });
    }

    private void initSession() {
        final String DEFAULT_ACCOUNT_NAME = "demo";
        final String DEFAULT_LOCATION = "mobile";
        numberOfBadge.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        session = Webim.newSessionBuilder()
                .setContext(this)
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
                        Webim.SessionBuilder.WebimLogVerbosityLevel.VERBOSE)
//                .setVisitorFieldsJson("{\"id\":\"1234567890987654321\",\"display_name\":\"Никита\",\"crc\":\"ffadeb6aa3c788200824e311b9aa44cb\"}")
//                .setVisitorDataPreferences(getSharedPreferences("test2", Context.MODE_PRIVATE))
                .build();
        session.getStream().setUnreadByVisitorMessageCountChangeListener(new MessageStream.UnreadByVisitorMessageCountChangeListener() {
            @Override
            public void onUnreadByVisitorMessageCountChanged(int newMessageCount) {
                if (newMessageCount > 0) {
                    numberOfBadge.setText(String.valueOf(newMessageCount));
                    numberOfBadge.setVisibility(View.VISIBLE);
                } else {
                    numberOfBadge.setVisibility(View.GONE);
                }
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    protected void onResume() {
        super.onResume();
        initSession();
        session.resume();
    }

    protected void onPause() {
        super.onPause();
        session.destroy();
    }
}