package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import com.webimapp.android.sdk.FAQItem;
import com.webimapp.android.sdk.impl.items.responses.ErrorResponse;

import java.util.List;

public class FAQItemItem extends ErrorResponse implements FAQItem {
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
}
