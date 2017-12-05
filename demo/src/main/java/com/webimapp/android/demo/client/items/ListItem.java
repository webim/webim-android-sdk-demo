package com.webimapp.android.demo.client.items;

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;

import com.webimapp.android.demo.client.MessagesAdapter;

public abstract class ListItem {
    private static long idCounter;
    private long id = ++idCounter;

    /** @see android.widget.Adapter#getItemId(int) */
    public long getIncrementalId() {
        return id;
    }

    /** @see android.widget.Adapter#getItemViewType(int) */
    public abstract ViewType getViewType();

    /** @see android.widget.Adapter#getView(int, View, ViewGroup) */
    public abstract View getView(MessagesAdapter adapter,
                                 @Nullable View convertView,
                                 ViewGroup parent, ListItem prev);
}
