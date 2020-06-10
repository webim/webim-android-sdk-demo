package com.webimapp.android.demo.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    private static final Uri LINK_PRIVACY = Uri.parse("https://webim.ru/privacy/");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initActionBar();
        initTextPrivacy();
        initTextVersion();
        getFragmentManager().beginTransaction()
                .replace(R.id.container, new SettingsFragment())
                .commit();
    }

    private void initActionBar() {
        this.setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        final ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }
    }

    private void initTextPrivacy() {
        TextView textPrivacy = findViewById(R.id.textPrivacy);
        textPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                browserIntent.setData(LINK_PRIVACY);
                startActivity(browserIntent);
            }
        });
    }

    private void initTextVersion() {
        TextView textVersion = findViewById(R.id.textVersion);
        String appVersion = BuildConfig.VERSION_NAME;
        String sdkVersion = com.webimapp.android.sdk.BuildConfig.VERSION_NAME;
        textVersion.setText(getString(R.string.prefs_version, appVersion, sdkVersion));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
    }
}
