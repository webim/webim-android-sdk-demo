package com.webimapp.android.demo.client.util;

import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.AbsListView;

public abstract class EndlessScrollListener implements AbsListView.OnScrollListener {
    private int visibleThreshold;
    private boolean loading = false;
    private FloatingActionButton button = null;

    public EndlessScrollListener() {
        this(5);
    }

    public EndlessScrollListener(int visibleThreshold) {
        this.visibleThreshold = visibleThreshold;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {
        if (button != null) {
            if (totalItemCount - firstVisibleItem > visibleItemCount + 1) {
                button.setVisibility(View.VISIBLE);
            } else {
                button.setVisibility(View.GONE);
            }
        }
        if (!loading && (firstVisibleItem - visibleThreshold) <= 0) {
            loading = true;
            onLoadMore(totalItemCount);
        }
    }

    public void setButton(FloatingActionButton button) {
        this.button = button;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public abstract void onLoadMore(int totalItemsCount);

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }
}
