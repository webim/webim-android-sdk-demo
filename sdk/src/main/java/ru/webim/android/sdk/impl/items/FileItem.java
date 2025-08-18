package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class FileItem {
    @SerializedName("file")
    private File file;
    @SerializedName("extra_text")
    private String extraText;

    public File getFile() {
        return file;
    }

    public String getExtraText() {
        return extraText;
    }

    public static final class File {
        @SerializedName("desc")
        private FileParametersItem desc;

        @SerializedName("error")
        private String error = "";

        @SerializedName("error_message")
        private String error_message = "";

        @SerializedName("visitor_error_message")
        private String visitorErrorMessage = "";

        @SerializedName("progress")
        private int progress = 0;

        @SerializedName("state")
        private FileState state;

        public enum FileState {
            @SerializedName("error")
            ERROR,

            @SerializedName("ready")
            READY,

            @SerializedName("upload")
            UPLOAD,

            @SerializedName("external_checks")
            EXTERNAL_CHECKS
        }

        public FileParametersItem getProperties() {
            return desc;
        }

        public String getErrorType() {
            return error;
        }

        public String getErrorMessage() {
            return error_message;
        }

        public String getVisitorErrorMessage() {
            return visitorErrorMessage;
        }

        public int getDownloadProgress() {
            return progress;
        }

        public FileState getState() {
            return state;
        }
    }
}