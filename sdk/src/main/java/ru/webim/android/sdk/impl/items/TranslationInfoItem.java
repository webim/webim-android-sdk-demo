package ru.webim.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;

public class TranslationInfoItem {

    @SerializedName("translation_info")
    private TranslationInfo translationInfo;

    public TranslationInfo getTranslationInfo() {
        return translationInfo;
    }

    public static final class TranslationInfo {

        @SerializedName("translated_text")
        private String translatedText;

        @SerializedName("source_lang")
        private String sourceLang;

        @SerializedName("target_lang")
        private String targetLang;

        @SerializedName("error")
        private String error;

        public String getTranslatedText() {
            return translatedText;
        }

        public String getSourceLang() {
            return sourceLang;
        }

        public String getTargetLang() {
            return targetLang;
        }

        public String getError() {
            return error;
        }
    }
}
