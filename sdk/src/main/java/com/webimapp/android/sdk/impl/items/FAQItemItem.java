package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import com.webimapp.android.sdk.FAQItem;

import java.util.List;

public class FAQItemItem implements FAQItem {
    @SerializedName("id")
    private String id;
    @SerializedName("categories")
    private List<Integer> categories;
    @SerializedName("title")
    private String title;
    @SerializedName("tags")
    private List<String> tags;
    @SerializedName("content")
    private String content;
    @SerializedName("likes")
    private int likes;
    @SerializedName("dislikes")
    private int dislikes;
    @SerializedName("userRate")
    private String userRate;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<Integer> getCategories() {
        return categories;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<String> getTags() {
        return tags;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public int getLikeCount() {
        return likes;
    }

    @Override
    public int getDislikeCount() {
        return dislikes;
    }

    @Override
    public UserRate getUserRate() {
        if (userRate == null) {
            return UserRate.NO_RATE;
        }
        switch (userRate) {
            case "like":
                return UserRate.LIKE;
            case "dislike":
                return UserRate.DISLIKE;
            default:
                return UserRate.NO_RATE;
        }
    }

    public enum FAQUserRateKind {
        @SerializedName("item")
        ITEM,
        @SerializedName("category")
        CATEGORY
    }
}
