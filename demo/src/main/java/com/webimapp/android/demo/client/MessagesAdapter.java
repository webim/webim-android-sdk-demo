package com.webimapp.android.demo.client;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.webimapp.android.demo.client.items.ListItem;
import com.webimapp.android.demo.client.items.ViewType;

import java.util.List;

public class MessagesAdapter extends BaseAdapter {

    private static final int VIEW_TYPE_COUNT = ViewType.values().length;
    private final LayoutInflater inflater;
    private final List<ListItem> list;
    private final Context context;
    private final @Nullable OnClickListener onAvatarClickListener;
    private final java.text.DateFormat dateFormat;

    public MessagesAdapter(Context context, List<ListItem> list) {
        this(context, list, null);
    }

    public MessagesAdapter(Context context, List<ListItem> list, @Nullable OnClickListener listener) {
        this.context = context;
        this.list = list;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.onAvatarClickListener = listener;
        this.dateFormat = DateFormat.getTimeFormat(context);
    }

    @Override
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        ListItem item = list.get(position);
        if(convertView != null) {
            Object tag = convertView.getTag(R.id.listItem);
            if(tag == item)
                return convertView;
            if(((ListItem)tag).getViewType() != item.getViewType())
                convertView = null;
        }
        View view = item.getView(this, convertView, parent);
        view.setTag(R.id.listItem, item);
        return view;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public ListItem getItem(int position) {
        return position < list.size() ? list.get(position) : null;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        ListItem item = getItem(position);
        return item == null ? 0 : item.getIncrementalId();
    }

    @Override
    public int getItemViewType(int position) {
        return position < list.size() ? list.get(position).getViewType().ordinal() : 0;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    public LayoutInflater getInflater() {
        return inflater;
    }

    public Context getContext() {
        return context;
    }

    @Nullable
    public OnClickListener getOnAvatarClickListener() {
        return onAvatarClickListener;
    }

    public java.text.DateFormat getDateFormat() {
        return dateFormat;
    }
}