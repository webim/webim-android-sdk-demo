package ru.webim.android.demo;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.view.MenuItem;
import android.widget.Toast;

import ru.webim.android.demo.util.WebimSessionDirector;
import ru.webim.android.sdk.Webim;

public class WebimChatActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_RATING_BAR_ON_STARTUP = "extra_show_rating_bar_on_startup";
    private static boolean active;
    private WidgetChatFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        setContentView(R.layout.activity_webim_chat);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int authUser = sharedPreferences.getInt(SettingsFragment.KEY_AUTH_VISITOR, SettingsFragment.DEFAULT_VISITOR);
        if (authUser != SettingsFragment.DEFAULT_VISITOR) {
            createSessionWithAuthVisitor();
        } else {
            createSessionWithAnonymousVisitor();
        }
    }

    private void createSessionWithAuthVisitor() {
        WebimSessionDirector
            .createSessionBuilderWithAuth2Visitor(this, new WebimSessionDirector.OnSessionBuilderCreatedListener() {
                @Override
                public void onSessionBuilderCreated(Webim.SessionBuilder sessionBuilder) {
                    initFragment(sessionBuilder);
                }

                @Override
                public void onError(WebimSessionDirector.SessionBuilderError error) {
                    Toast.makeText(WebimChatActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void createSessionWithAnonymousVisitor() {
        WebimSessionDirector
            .createSessionBuilderWithAnonymousVisitor(this, Webim.PushSystem.FCM, new WebimSessionDirector.OnSessionBuilderCreatedListener() {
                @Override
                public void onSessionBuilderCreated(Webim.SessionBuilder sessionBuilder) {
                    initFragment(sessionBuilder);
                }

                @Override
                public void onError(WebimSessionDirector.SessionBuilderError error) {
                    Toast.makeText(WebimChatActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void initFragment(Webim.SessionBuilder sessionBuilder) {
        String fragmentByTag = WidgetChatFragment.class.getSimpleName();
        WidgetChatFragment currentFragment = (WidgetChatFragment) getSupportFragmentManager().findFragmentByTag(fragmentByTag);

        if (currentFragment == null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            currentFragment = new WidgetChatFragment();
            currentFragment.setWebimSessionBuilder(sessionBuilder);

            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.webimChatContainer, currentFragment, fragmentByTag)
                .commit();
        }
        fragment = currentFragment;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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
        fragment.onBackPressed();

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
