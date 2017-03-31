package com.webimapp.android.demo.client.util;

import android.widget.AbsListView;

public class CombinedScrollListener implements AbsListView.OnScrollListener {
    private final AbsListView.OnScrollListener first;
    private final AbsListView.OnScrollListener second;

    public CombinedScrollListener(AbsListView.OnScrollListener first, AbsListView.OnScrollListener second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        first.onScrollStateChanged(view, scrollState);
        second.onScrollStateChanged(view, scrollState);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        first.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        second.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }
}
