package com.webimapp.android.sdk.impl.items.responses;

import com.webimapp.android.sdk.impl.items.FileParametersItem;

public class UploadResponse extends DefaultResponse {
    private FileParametersItem data;

    public UploadResponse() {

    }

    public FileParametersItem getData() {
        return data;
    }
}
