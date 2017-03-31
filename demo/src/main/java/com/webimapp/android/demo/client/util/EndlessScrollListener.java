package com.webimapp.android.demo.client.util;

import android.widget.AbsListView;

public abstract class EndlessScrollListener implements AbsListView.OnScrollListener {
    private int visibleThreshold;
    private boolean loading = false;

    public EndlessScrollListener() {
        this(5);
    }

    public EndlessScrollListener(int visibleThreshold) {
        this.visibleThreshold = visibleThreshold;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if(!loading && (firstVisibleItem - visibleThreshold) <= 0 ) {
            loading = true;
            onLoadMore(totalItemCount);
        }
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public abstract void onLoadMore(int totalItemsCount);

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }
}
