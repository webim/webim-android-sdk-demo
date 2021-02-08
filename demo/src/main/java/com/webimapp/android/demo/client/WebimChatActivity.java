package com.webimapp.android.demo.client;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.webimapp.android.demo.client.util.WebimSessionDirector;
import com.webimapp.android.sdk.FatalErrorHandler;
import com.webimapp.android.sdk.WebimError;

public class WebimChatActivity extends AppCompatActivity implements FatalErrorHandler {
    private static boolean active;
    private WebimChatFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // Prevents fragment recovery (we need to set session)
        setContentView(R.layout.activity_webim_chat);

        fragment = new WebimChatFragment();
        fragment.setWebimSession(WebimSessionDirector
                .createSessionBuilderWithAnonymousVisitor(this)
                .setErrorHandler(this)
                .build());
        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.webimChatContainer, fragment)
                .commit();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onError(@NonNull WebimError<FatalErrorType> error) {
        switch (error.getErrorType()) {
            default:
                if (!BuildConfig.DEBUG) {
                    FirebaseCrashlytics.getInstance().recordException(
                            new Throwable("Handled unknown webim error: " + error.getErrorString()));
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

    @Override
    public void onBackPressed() {
        if (fragment.isChatMenuVisible()) {
            fragment.hideChatMenu();
            return;
        }
        super.onBackPressed();
        overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
