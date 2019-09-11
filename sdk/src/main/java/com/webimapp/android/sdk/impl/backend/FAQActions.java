package com.webimapp.android.sdk.impl.backend;

import android.support.annotation.NonNull;

import com.webimapp.android.sdk.impl.items.FAQCategoryItem;
import com.webimapp.android.sdk.impl.items.FAQItemItem;
import com.webimapp.android.sdk.impl.items.FAQSearchItemItem;
import com.webimapp.android.sdk.impl.items.FAQStructureItem;
import com.webimapp.android.sdk.impl.items.responses.DefaultResponse;

import java.util.List;

import retrofit2.Call;

public class FAQActions {
    @NonNull
    private final FAQRequestLoop requestLoop;
    @NonNull
    private final FAQService faq;
    private static final String CHARACTERS_TO_ENCODE = "\n!#$&'()*+,/:;=?@[] \"%-.<>\\^_`{|}~";

    FAQActions(@NonNull FAQService faq, @NonNull FAQRequestLoop faqRequestLoop) {
        this.faq = faq;
        this.requestLoop = faqRequestLoop;
    }

    private void enqueue(FAQRequestLoop.WebimRequest<?> request) {
        requestLoop.enqueue(request);
    }

    public void getItem(final String itemId,
                        final String deviceId,
                        @NonNull final DefaultCallback<FAQItemItem> callback) {
        callback.getClass(); // NPE
        enqueue(new FAQRequestLoop.WebimRequest<FAQItemItem>(true) {
            @Override
            public Call<FAQItemItem> makeRequest() {
                return faq.getItem(itemId, deviceId);
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
                            final String deviceId,
                            @NonNull final DefaultCallback<FAQCategoryItem> callback) {
        callback.getClass(); // NPE
        enqueue(new FAQRequestLoop.WebimRequest<FAQCategoryItem>(true) {
            @Override
            public Call<FAQCategoryItem> makeRequest() {
                return faq.getCategory(categoryId, deviceId);
            }

            @Override
            public void runCallback(FAQCategoryItem response) {
                callback.onSuccess(response);
            }
        });
    }

    public void getSearch(final String query, final int categoryId, final int limit,
                          @NonNull final DefaultCallback<List<FAQSearchItemItem>> callback) {
        callback.getClass(); //NPE
        enqueue(new FAQRequestLoop.WebimRequest<List<FAQSearchItemItem>>(true) {
            @Override
            public Call<List<FAQSearchItemItem>> makeRequest() {
                return faq.getSearch(percentEncode(query), categoryId, limit);
            }

            @Override
            public void runCallback(List<FAQSearchItemItem> response) {
                callback.onSuccess(response);
            }
        });
    }

    public void like(final String itemId, final String deviceId) {
        enqueue(new FAQRequestLoop.WebimRequest<DefaultResponse>(true) {
            @Override
            public Call<DefaultResponse> makeRequest() {
                return faq.like(itemId, deviceId);
            }
        });
    }

    public void dislike(final String itemId, final String deviceId) {
        enqueue(new FAQRequestLoop.WebimRequest<DefaultResponse>(true) {
            @Override
            public Call<DefaultResponse> makeRequest() {
                return faq.dislike(itemId, deviceId);
            }
        });
    }

    public void getCategoriesForApplication(final String application,
                                            final String language,
                                            final String departmentKey,
                                            final DefaultCallback<List<Integer>> callback) {
        enqueue(new FAQRequestLoop.WebimRequest<List<Integer>>(true) {
            @Override
            public Call<List<Integer>> makeRequest() {
                return faq.getCategoriesForApplication(application, "android", language, departmentKey);
            }

            @Override
            public void runCallback(List<Integer> response) {
                callback.onSuccess(response);
            }
        });

    }

    private static String percentEncode(String input) {
        if ((input == null) || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder(input);
        for (int i = (input.length() - 1); i >= 0; i--) {
            if (CHARACTERS_TO_ENCODE.indexOf(input.charAt(i)) != -1) {
                result.replace(
                        i,
                        (i + 1),
                        ("%" + Integer.toHexString(0x100 | input.charAt(i))
                                .substring(1).toUpperCase())
                );
            }
        }

        return result.toString();
    }
}
