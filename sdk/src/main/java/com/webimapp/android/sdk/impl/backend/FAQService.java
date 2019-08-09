package com.webimapp.android.sdk.impl.backend;

import com.webimapp.android.sdk.impl.items.FAQCategoryItem;
import com.webimapp.android.sdk.impl.items.FAQItemItem;
import com.webimapp.android.sdk.impl.items.FAQSearchItemItem;
import com.webimapp.android.sdk.impl.items.FAQStructureItem;
import com.webimapp.android.sdk.impl.items.responses.DefaultResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface FAQService {

    String PARAMETER_ITEM_ID = "itemid";
    String PARAMETER_CATEGORY_ID = "categoryid";
    String PARAMETER_QUERY = "query";
    String PARAMETER_LIMIT = "limit";
    String PARAMETER_USER_ID = "userid";
    String URL_SUFFIX_ITEM = "/services/faq/v1/item";
    String URL_SUFFIX_CATEGORY = "/services/faq/v1/category";
    String URL_SUFFIX_STRUCTURE = "/services/faq/v1/structure";
    String URL_SUFFIX_SEARCH = "/services/faq/v1/search";
    String URL_SUFFIX_LIKE = "/services/faq/v1/like";
    String URL_SUFFIX_DISLIKE = "/services/faq/v1/dislike";

    @GET(URL_SUFFIX_ITEM)
    Call<FAQItemItem> getItem(
            @Query(PARAMETER_ITEM_ID) String itemId,
            @Query(PARAMETER_USER_ID) String userId
    );

    @GET(URL_SUFFIX_CATEGORY)
    Call<FAQCategoryItem> getCategory(
            @Query(PARAMETER_CATEGORY_ID) int categoryId,
            @Query(PARAMETER_USER_ID) String userId
    );

    @GET(URL_SUFFIX_STRUCTURE)
    Call<FAQStructureItem> getStructure(
            @Query(PARAMETER_CATEGORY_ID) int categoryId
    );

    @GET(URL_SUFFIX_SEARCH)
    Call<List<FAQSearchItemItem>> getSearch(
            @Query(PARAMETER_QUERY) String query,
            @Query(PARAMETER_CATEGORY_ID) int categoryId,
            @Query(PARAMETER_LIMIT) int limit
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_LIKE)
    Call<DefaultResponse> like(
            @Field(PARAMETER_ITEM_ID) String itemId,
            @Field(PARAMETER_USER_ID) String userId
    );

    @FormUrlEncoded
    @POST(URL_SUFFIX_DISLIKE)
    Call<DefaultResponse> dislike(
            @Field(PARAMETER_ITEM_ID) String itemId,
            @Field(PARAMETER_USER_ID) String userId
    );
}
