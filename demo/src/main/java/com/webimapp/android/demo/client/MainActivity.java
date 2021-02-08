package com.webimapp.android.demo.client;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.webimapp.android.demo.client.util.WebimSessionDirector;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.WebimSession;

public class MainActivity extends AppCompatActivity {

    public static String NEED_OPEN_CHAT = "need_open_chat";
    private WebimSession session;
    private ProgressBar progressBar;
    private TextView numberOfBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        }
        if (needOpenChat()) {
            openChatActivity();
        }
        setContentView(R.layout.activity_main);
        initNewChatButton();
        initViewForBadge();
        initSettingButton();
        initSession();
    }

    private boolean needOpenChat() {
        return getIntent().getBooleanExtra(NEED_OPEN_CHAT, false);
    }

    private void initNewChatButton() {
        Button newChatButton = findViewById(R.id.buttonStartChat);
        newChatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openChatActivity();
            }
        });
    }

    private void openChatActivity() {
        startActivity(new Intent(MainActivity.this, WebimChatActivity.class));
        overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
    }

    private void initViewForBadge() {
        progressBar = findViewById(R.id.progressBar);
        numberOfBadge = findViewById(R.id.textNumberOfBadge);
    }

    private void initSettingButton() {
        Button settingsButton = findViewById(R.id.buttonSettings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
            }
        });
    }

    private void initSession() {
        numberOfBadge.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        session = WebimSessionDirector.createSessionBuilderWithAnonymousVisitor(this).build();
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (needOpenChat()) {
            openChatActivity();
        }
    }
}