package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import com.webimapp.android.sdk.WebimPushNotification;

public class WebimPushNotificationImpl implements WebimPushNotification {
    @SerializedName("event")
    private String event;
    @SerializedName("type")
    private NotificationType type;
    @SerializedName("params")
    private List<String> params;

    @NonNull
    @Override
    public NotificationType getType() {
        return type;
    }

    @NonNull
    @Override
    public String getEvent() {
        return event;
    }

    @NonNull
    @Override
    public List<String> getParams() {
        return params;
    }
}
