package com.webimapp.android.sdk.impl.backend;

public interface SurveyQuestionCallback {
    void onSuccess();

    void onFailure(String error);
}
