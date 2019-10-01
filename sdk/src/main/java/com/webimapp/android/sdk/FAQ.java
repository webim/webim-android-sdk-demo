package com.webimapp.android.sdk;

import java.util.List;

public interface FAQ {

    /**
     * Resumes FAQ networking.
     * Notice that a FAQ is created as a paused, i.e. to start using it
     * the first thing to do is to call this method
     * @throws IllegalStateException if the FAQ was destroyed
     * @throws RuntimeException if the method was called not from the thread the FAQ was created in
     */
    void resume();

    /**
     * Pauses session networking.
     * @throws RuntimeException if the method was called not from the thread the FAQ was created in
     */
    void pause();

    /**
     * Destroys FAQ. It is impossible to use any FAQ methods after it was destroyed.
     * @throws RuntimeException if the method was called not from the thread the FAQ was created in
     */
    void destroy();

    /**
     * Requests structure. If nil is passed inside completion, there no structure with this id.
     * @param rootId root id of FAQ tree
     * @param callback callback to be called on category if method call succeeded
     */
    void getStructure(int rootId, GetCallback<FAQStructure> callback);

    /**
     * Requests category. If nil is passed inside completion, there no category with this id.
     * @param id category id
     * @param callback callback to be called on category if method call succeeded
     */
    void getCategory(int id, GetCallback<FAQCategory> callback);

    /**
     * Requests category. If nil is passed inside completion, there no category with this id.
     * @param callback callback to be called on category if method call succeeded
     */
    void getCategoriesForApplication(GetCallback<List<Integer>> callback);

    /**
     * Requests category from cache. If nil is passed inside completion, there no category with this id.
     * @param id category id
     * @param callback callback to be called on category if method call succeeded
     */
    void getCachedCategory(int id, GetCallback<FAQCategory> callback);

    /**
     * Requests item. If nil is passed inside completion, there no item with this id.
     * @param id item id
     * @param callback callback to be called on category if method call succeeded
     */
    void getItem(String id, GetCallback<FAQItem> callback);

    /**
     * Requests search. If nil is passed inside completion, there no search result with this query.
     * @param query search query
     * @param id item id
     * @param limit limit the number of items in the response.
     * @param callback callback to be called on category if method call succeeded
     */
    void search(String query, int id, int limit, GetCallback<List<FAQSearchItem>> callback);

    /**
     * Requests like.
     * @param item item to like
     */
    void like(FAQItem item);

    /**
     * Requests dislike.
     * @param item item to dislike
     */
    void dislike(FAQItem item);

    interface GetCallback<T> {
        void receive(T item);

        void onError();
    }
}
