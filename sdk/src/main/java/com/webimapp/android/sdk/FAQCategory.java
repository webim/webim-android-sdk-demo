package com.webimapp.android.sdk;

import java.util.List;

public interface FAQCategory {
    /**
     * @return category id
     */
    int getId();

    /**
     * @return category title
     */
    String getTitle();

    /**
     *
     * @return list of category items.
     */
    List<FAQItem> getItems();

    /**
     *
     * @return list of subcategories
     */
    List<FAQCategoryInfo> getSubCategories();
}
