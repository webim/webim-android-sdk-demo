package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;

import com.webimapp.android.sdk.impl.items.FAQCategoryItem;
import com.webimapp.android.sdk.impl.items.FAQItemItem;
import com.webimapp.android.sdk.impl.items.FAQStructureItem;

import retrofit2.Call;

public class FAQActions {
    @NonNull
    private final FAQRequestLoop requestLoop;
    @NonNull
    private final FAQService faq;

    FAQActions(@NonNull FAQService faq, @NonNull FAQRequestLoop faqRequestLoop) {
        this.faq = faq;
        this.requestLoop = faqRequestLoop;
    }

    private void enqueue(FAQRequestLoop.WebimRequest<?> request) {
        requestLoop.enqueue(request);
    }

    public void getItem(final String itemId,
                        @NonNull final DefaultCallback<FAQItemItem> callback) {
        callback.getClass(); // NPE
        enqueue(new FAQRequestLoop.WebimRequest<FAQItemItem>(true) {
            @Override
            public Call<FAQItemItem> makeRequest() {
                return faq.getItem(itemId);
            }

            @Override
            public void runCallback(FAQItemItem response) {
                callback.onSuccess(response);
            }
        });
    }

    public void getStructure(final int structureId,
                            @NonNull final DefaultCallback<FAQStructureItem> callback) {
        callback.getClass(); // NPE
        enqueue(new FAQRequestLoop.WebimRequest<FAQStructureItem>(true) {
            @Override
            public Call<FAQStructureItem> makeRequest() {
                return faq.getStructure(structureId);
            }

            @Override
            public void runCallback(FAQStructureItem response) {
                callback.onSuccess(response);
            }
        });
    }

    public void getCategory(final int categoryId,
                            @NonNull final DefaultCallback<FAQCategoryItem> callback) {
        callback.getClass(); // NPE
        enqueue(new FAQRequestLoop.WebimRequest<FAQCategoryItem>(true) {
            @Override
            public Call<FAQCategoryItem> makeRequest() {
                return faq.getCategory(categoryId);
            }

            @Override
            public void runCallback(FAQCategoryItem response) {
                callback.onSuccess(response);
            }
        });
    }
}
