package com.webimapp.android.sdk.impl.backend;

import com.webimapp.android.sdk.impl.items.FAQCategoryItem;
import com.webimapp.android.sdk.impl.items.FAQItemItem;
import com.webimapp.android.sdk.impl.items.FAQStructureItem;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface FAQService {

    String PARAMETER_ITEM_ID = "itemid";
    String PARAMETER_CATEGORY_ID = "categoryid";
    String URL_SUFFIX_ITEM = "/services/faq/v1/item";
    String URL_SUFFIX_CATEGORY = "/services/faq/v1/category";
    String URL_SUFFIX_STRUCTURE = "/services/faq/v1/structure";

    @GET(URL_SUFFIX_ITEM)
    Call<FAQItemItem> getItem(
            @Query(PARAMETER_ITEM_ID) String itemId
    );

    @GET(URL_SUFFIX_CATEGORY)
    Call<FAQCategoryItem> getCategory(
            @Query(PARAMETER_CATEGORY_ID) int categoryId
    );

    @GET(URL_SUFFIX_STRUCTURE)
    Call<FAQStructureItem> getStructure(
            @Query(PARAMETER_CATEGORY_ID) int categoryId
    );

}
