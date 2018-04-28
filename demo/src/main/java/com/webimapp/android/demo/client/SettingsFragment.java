package com.webimapp.android.demo.client;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
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
        updatePreference("account");
        updatePreference("location");
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreference(key);
    }

    private void updatePreference(String key) {
        Preference preference = findPreference(key);
        if (preference instanceof EditTextPreference) {
            String defText = "";
            switch (key) {
                case "account":
                    defText = getString(R.string.prefs_account_summary);
                    break;
                case "location":
                    defText = getString(R.string.prefs_location_summary);
                    break;
            }
            EditTextPreference textPreference = (EditTextPreference) preference;
            if (textPreference.getText().trim().length() > 0) {
                textPreference.setSummary(textPreference.getText());
            } else {
                textPreference.setSummary(defText);
            }
        }
    }
}
