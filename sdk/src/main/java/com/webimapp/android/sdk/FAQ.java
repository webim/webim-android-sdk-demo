package com.webimapp.android.sdk;

/**
 *
 */
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
    void getStructure(int rootId, GetStructureCallback callback);

    /**
     * Requests category. If nil is passed inside completion, there no category with this id.
     * @param id category id
     * @param callback callback to be called on category if method call succeeded
     */
    void getCategory(int id, GetCategoryCallback callback);

    /**
     * Requests item. If nil is passed inside completion, there no item with this id.
     * @param id item id
     * @param callback callback to be called on category if method call succeeded
     */
    void getItem(String id, GetItemCallback callback);

    /**
     * @see FAQ#getStructure(int, GetStructureCallback)
     */
    interface GetStructureCallback {
        void receive(FAQStructure structure);
    }

    /**
     * @see FAQ#getCategory(int, GetCategoryCallback)
     */
    interface GetCategoryCallback {
        void receive(FAQCategory category);
    }

    /**
     * @see FAQ#getItem(String, GetItemCallback)
     */
    interface GetItemCallback {
        void receive(FAQItem item);
    }
}
