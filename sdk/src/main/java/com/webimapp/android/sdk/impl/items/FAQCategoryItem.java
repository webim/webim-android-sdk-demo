package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import com.webimapp.android.sdk.FAQCategory;
import com.webimapp.android.sdk.FAQCategoryInfo;
import com.webimapp.android.sdk.FAQItem;
import com.webimapp.android.sdk.impl.items.responses.ErrorResponse;

import java.util.ArrayList;
import java.util.List;

public class FAQCategoryItem extends ErrorResponse implements FAQCategory {
    @SerializedName("categoryid")
    private int categoryId;
    @SerializedName("title")
    private String title;
    @SerializedName("childs")
    private List<ChildItem> childs;

    @Override
    public int getId() {
        return categoryId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<FAQItem> getItems() {
        List<FAQItem> items = new ArrayList<>();
        for (ChildItem childItem : childs) {
            if (childItem.type == FAQCategoryItemKind.ITEM) {
                items.add((FAQItemItem)childItem.data);
            }
        }
        return items;
    }

    @Override
    public List<FAQCategoryInfo> getSubCategories() {
        List<FAQCategoryInfo> subCategories = new ArrayList<>();
        for (ChildItem childItem : childs) {
            if (childItem.type == FAQCategoryItemKind.CATEGORY) {
                subCategories.add((FAQCategoryInfoItem)childItem.data);
            }
        }
        return subCategories;
    }

    public class ChildItem<T> {
        @SerializedName("type")
        private FAQCategoryItemKind type;
        @SerializedName("data")
        private T data;
    }

    public enum FAQCategoryItemKind {
        @SerializedName("item")
        ITEM,
        @SerializedName("category")
        CATEGORY
    }
}
