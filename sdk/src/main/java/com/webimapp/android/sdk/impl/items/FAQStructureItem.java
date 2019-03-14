package com.webimapp.android.sdk.impl.items;

import com.google.gson.annotations.SerializedName;
import com.webimapp.android.sdk.FAQStructure;
import com.webimapp.android.sdk.impl.items.responses.ErrorResponse;

import java.util.LinkedList;
import java.util.List;

public class FAQStructureItem extends ErrorResponse implements FAQStructure {
    @SerializedName("id")
    private String id;
    @SerializedName("type")
    private FAQCategoryItem.FAQCategoryItemKind type;
    @SerializedName("title")
    private String title;
    @SerializedName("childs")
    private List<FAQStructureItem> childs;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public FAQType getType() {
        switch (type) {
            case ITEM:
                return FAQType.ITEM;
            case CATEGORY:
                return FAQType.CATEGORY;
            default:
                return FAQType.UNKNOWN;
        }
    }

    @Override
    public List<FAQStructure> getChildren() {
        return new LinkedList<FAQStructure>(childs);
    }

    @Override
    public String getTitle() {
        return title;
    }
}
