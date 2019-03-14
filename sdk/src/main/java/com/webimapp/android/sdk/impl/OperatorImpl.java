package com.webimapp.android.sdk.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.webimapp.android.sdk.Operator;

public class OperatorImpl implements Operator {
    private final @NonNull Operator.Id id;
    private final @NonNull String name;
    private final @Nullable String avatarUrl;

    public OperatorImpl(@NonNull Id id, @NonNull String name, @Nullable String avatarUrl) {
        id.getClass(); // NPE
        name.getClass(); // NPE
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
    }

    @NonNull
    @Override
    public Id getId() {
        return id;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public String getAvatarUrl() {
        return avatarUrl;
    }


}
