package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class FileParametersItem {
    @SerializedName("size")
    private long size;
    @SerializedName("guid")
    private String guid;
    @SerializedName("content_type")
    private String content_type;
    @SerializedName("filename")
    private String filename;
    @SerializedName("image")
    private WMImageParams image;

    public long getSize() {
        return size;
    }

    public String getGuid() {
        return guid;
    }

    public String getContentType() {
        return content_type;
    }

    public String getFilename() {
        return filename;
    }

    public WMImageParams getImageParams() {
        return image;
    }

    public static class WMImageParams {

        @SerializedName("size")
        private WMImageSize size;

        public WMImageSize getSize() {
            return size;
        }

        public static class WMImageSize {

            @SerializedName("width")
            private int width;
            @SerializedName("height")
            private int height;

            public int getWidth() {
                return width;
            }

            public int getHeight() {
                return height;
            }

        }
    }

}
