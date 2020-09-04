package com.webimapp.android.demo.client;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Patterns;

import java.util.regex.Pattern;

public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_LOCATION = "location";
    private static final String DEFAULT_ACCOUNT = "demo";
    private static final String DEFAULT_LOCATION = "mobile";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        updatePreference(KEY_ACCOUNT);
        updatePreference(KEY_LOCATION);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        checkFieldsValidity();
    }

    private void checkFieldsValidity() {
        EditTextPreference accountPreference = (EditTextPreference) findPreference(KEY_ACCOUNT);
        if (!isFieldValid(KEY_ACCOUNT, accountPreference.getText())) {
            accountPreference.setText(DEFAULT_ACCOUNT);
        }
        EditTextPreference locationPreference = (EditTextPreference) findPreference(KEY_LOCATION);
        if (!isFieldValid(KEY_LOCATION, locationPreference.getText())) {
            locationPreference.setText(DEFAULT_LOCATION);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreference(key);
    }

    private void updatePreference(String key) {
        Preference preference = findPreference(key);
        if (preference instanceof EditTextPreference) {
            EditTextPreference textPreference = (EditTextPreference) preference;
            String text = textPreference.getText();

            if (!text.isEmpty() && (Character.isWhitespace(text.charAt(0)) ||
                    Character.isWhitespace(text.charAt(text.length() - 1)))) {
                textPreference.setText(text.trim());
                return;
            }

            if (text.isEmpty()) {
                switch (key) {
                    case KEY_ACCOUNT:
                        textPreference.setSummary(R.string.prefs_account_summary);
                        break;
                    case KEY_LOCATION:
                        textPreference.setSummary(R.string.prefs_location_summary);
                        break;
                }
            } else {
                if (key.equals(KEY_ACCOUNT) && !isFieldValid(KEY_ACCOUNT, text)) {
                    textPreference.setText(DEFAULT_ACCOUNT);
                } else if (key.equals(KEY_LOCATION) && !isFieldValid(KEY_LOCATION, text)) {
                    textPreference.setText(DEFAULT_LOCATION);
                } else {
                    textPreference.setSummary(text);
                }
            }
        }
    }

    private boolean isFieldValid(String key, String url) {
        if (key.equals(KEY_ACCOUNT) && url.contains("://")) {
            return Patterns.WEB_URL.matcher(url).matches();
        }
        return Pattern.compile("^\\w+$").matcher(url).matches();
    }
}
