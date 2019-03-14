package com.webimapp.android.sdk;

import java.util.List;

public interface FAQItem {
    /**
     * @return item id
     */
    String getId();

    /**
     * @return list of categories that contains the item
     */
    List<Integer> getCategories();

    /**
     * @return item title
     */
    String getTitle();

    /**
     * @return list of item tags
     */
    List<String> getTags();

    /**
     * @return item content
     */
    String getContent();

    /**
     * @return like count of the item.
     */
    int getLikeCount();

    /**
     * @return dislike count of the item.
     */
    int getDislikeCount();
}
