package com.webimapp.android.sdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.webimapp.android.sdk.UploadedFile;

public class UploadedFileImpl implements UploadedFile {

    private long size;
    private String guid;
    private String contentType;
    private String filename;
    private String visitorId;
    private String clientContentType;

    public UploadedFileImpl(long size,
                            @NonNull String guid,
                            @NonNull String contentType,
                            @NonNull String filename,
                            @NonNull String visitorId,
                            @NonNull String clientContentType
    ) {
        this.size = size;
        this.guid = guid;
        this.contentType = contentType;
        this.filename = filename;
        this.visitorId = visitorId;
        this.clientContentType = clientContentType;
    }

    @Override
    public long getSize() {
        return size;
    }

    @NonNull
    @Override
    public String getGuid() {
        return guid;
    }

    @NonNull
    @Override
    public String getFileName() {
        return filename;
    }

    @Nullable
    @Override
    public String getContentType() {
        return contentType;
    }

    @NonNull
    @Override
    public String getVisitorId() {
        return visitorId;
    }

    @NonNull
    @Override
    public String getClientContentType() {
        return clientContentType;
    }

    @NonNull
    @Override
    public String toString() {
        return "{\"client_content_type\":\" " + clientContentType +
                " \",\"visitor_id\":\"" + visitorId +
                "\",\"filename\":\"" + filename  +
                "\",\"content_type\":\"" + contentType +
                "\",\"guid\":\"" + guid +
                "\",\"size\":"+ size +
                " }";
    }
}

