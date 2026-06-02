package ru.webim.chatview.ui;

import static ru.webim.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static ru.webim.android.sdk.Message.Type.VISITOR;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.webim.android.sdk.Message;
import ru.webim.android.sdk.MessageListener;
import ru.webim.android.sdk.MessageStream;
import ru.webim.android.sdk.MessageTracker;
import ru.webim.chatview.ChatAdapter;
import ru.webim.chatview.R;
import ru.webim.chatview.utils.EndlessScrollListener;
import ru.webim.chatview.utils.MessageItemAnimator;
import ru.webim.chatview.utils.PayloadType;
import ru.webim.chatview.utils.ViewUtils;

public class ChatList extends FrameLayout {
    private ChatPrompt chatPrompt;
    private RecyclerView recyclerView;
    private TextView chatEmptyText;
    private View scrollContainer;
    private TextView scrollCounter;
    private ProgressBar progressBar;
    private MessageStream stream;
    private ListControllerReadyCallback readyCallback;
    private boolean isAutoScrollToLastMsgEnabled = true;

    private final BroadcastReceiver fileDownloadedComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            String messageId = downloadingFiles.get(id);
            if (messageId != null) {
                downloadingFiles.remove(id);
                listController.renderMessage(messageId);
            }
        }
    };
    private final Map<Long, String> downloadingFiles = new HashMap<>();
    private final List<MessageTracker.MessagesSyncedListener> syncedWithServerCallbacks = new ArrayList<>();
    private ListController listController;

    public ChatList(@NonNull Context context) {
        this(context, null);
    }

    public ChatList(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.chv_chat_style);
    }

    public ChatList(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View rootView = LayoutInflater.from(context).inflate(R.layout.view_chatlist, this, true);
        setBackgroundColor(ViewUtils.resolveAttr(R.attr.chv_secondary_color, context));

        chatPrompt = rootView.findViewById(R.id.chv_notification_bar);
        recyclerView = rootView.findViewById(R.id.chv_chat_view);
        chatEmptyText = rootView.findViewById(R.id.chv_empty_chat);
        scrollContainer = rootView.findViewById(R.id.chv_scroll_container);
        scrollCounter = rootView.findViewById(R.id.chv_scroll_counter);
        progressBar = rootView.findViewById(R.id.chat_progress_bar);

        syncedWithServerCallbacks.add(chatPrompt);
        syncedWithServerCallbacks.add(() -> stream.setChatRead());
    }

    public ListController getListController() {
        return listController;
    }

    void setListControllerReady(ListControllerReadyCallback readyCallback) {
        this.readyCallback = readyCallback;
    }

    public void setSSLError() {
        chatPrompt.setState(ChatPrompt.State.SSL_ERROR);
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    public View getScrollButton() {
        return scrollContainer;
    }

    public TextView getChatEmptyText() {
        return chatEmptyText;
    }

    public void setAdapter(ChatAdapter<?> chatAdapter) {
        recyclerView.setAdapter(chatAdapter);
    }

    public ChatAdapter<?> getAdapter() {
        return (ChatAdapter<?>) recyclerView.getAdapter();
    }

    public void setAutoScrollToLastMsgEnabled(boolean isEnabled) {
        isAutoScrollToLastMsgEnabled = isEnabled;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (isInEditMode()) return;

        ContextCompat.registerReceiver(
                getContext(),
                fileDownloadedComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
        );

        listController = new ListController();
        if (readyCallback != null) {
            readyCallback.onReady(listController);
        }
        chatPrompt.setState(ChatPrompt.State.SYNCING);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (isInEditMode()) return;
        getContext().unregisterReceiver(fileDownloadedComplete);
    }

    public void setStream(MessageStream stream) {
        this.stream = stream;
    }

    public MessageStream getStream() {
        return stream;
    }

    public void saveFile(Message message, Uri uri) {
        Message.FileInfo fileInfo = message.getAttachment().getFileInfo();
        String messageId = message.getServerSideId();
        String filename = fileInfo.getFileName();
        filename = filename.length() >= (Byte.MAX_VALUE - 1) ? filename.substring(0, (Byte.MAX_VALUE - 1)) : filename;
        showToast(getContext().getString(R.string.saving_file, filename), Toast.LENGTH_SHORT);

        DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            DownloadManager.Request downloadRequest = new DownloadManager.Request(Uri.parse(fileInfo.getUrl()));
            downloadRequest.setTitle(filename);
            downloadRequest.allowScanningByMediaScanner();
            downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            downloadRequest.setDestinationUri(uri);

            long request = manager.enqueue(downloadRequest);
            downloadingFiles.put(request, messageId);
        } else {
            showToast(getContext().getString(R.string.saving_failed), Toast.LENGTH_SHORT);
        }
    }

    public List<MessageTracker.MessagesSyncedListener> getSyncedWithServerCallbacks() {
        return syncedWithServerCallbacks;
    }

    private void showToast(String messageToast, int lengthToast) {
        Toast.makeText(getContext(), messageToast, lengthToast).show();
    }

    interface ChatEmptyListener {
        void chatEmpty(boolean empty);
    }

    interface ListControllerReadyCallback {
        void onReady(ListController listController);
    }

    public class ListController implements MessageListener {
        private static final int MESSAGES_PER_REQUEST = 25;
        private final MessageTracker tracker;
        private final ChatAdapter<?> adapter;
        private final LinearLayoutManager layoutManager;
        private final EndlessScrollListener scrollListener;
        private final ChatEmptyListener messagesCountListener;
        private boolean requestingMessages;
        private boolean chatWasRead = false;

        private int newMessagesCount = 0;

        private ListController() {
            messagesCountListener = empty -> chatEmptyText.setVisibility(empty ? VISIBLE : GONE);

            layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, true);
            layoutManager.setStackFromEnd(false);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setItemAnimator(new MessageItemAnimator());
            adapter = (ChatAdapter<?>) recyclerView.getAdapter();
            tracker = stream.newMessageTracker(this);

            MessageTracker.MessagesSyncedListener syncedListener = () -> {
                for (MessageTracker.MessagesSyncedListener r : syncedWithServerCallbacks) {
                    r.messagesSynced();
                }
            };
            tracker.setMessagesSyncedListener(syncedListener);

            scrollContainer.setOnClickListener(view1 -> {
                scrollContainer.setVisibility(View.GONE);

                if (newMessagesCount > 0) {
                    recyclerView.smoothScrollToPosition(newMessagesCount - 1);
                } else {
                    recyclerView.smoothScrollToPosition(0);
                }

                resetNewMessagesCount();
            });
            scrollContainer.bringToFront();

            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy < 0 && scrollContainer.isShown()) {
                        scrollContainer.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onChatWasScrolledToEnd() {
                    if (!chatWasRead) {
                        chatWasRead = true;
                        stream.setChatRead();
                        resetNewMessagesCount();
                    }
                }
            };
            scrollListener.setLoading(true);
            scrollListener.setDownButton(scrollContainer);
            scrollListener.setAdapter(adapter);
            recyclerView.addOnScrollListener(scrollListener);
            requestMore(true);
        }

        public void renderMessage(String messageId) {
            adapter.invalidateMessage(messageId);
        }

        private void requestMore() {
            requestMore(false);
        }

        private void requestMore(final boolean firstCall) {
            requestingMessages = true;

            tracker.getNextMessages(MESSAGES_PER_REQUEST, received -> {
                chatWasRead = false;
                requestingMessages = false;
                progressBar.setVisibility(View.GONE);

                if (received.size() != 0) {
                    checkChatNotEmptyCallback(true);
                    adapter.addAll(0, received);
                    adapter.notifyItemRangeInserted(adapter.getItemCount() - 1, received.size());

                    if (firstCall) {
                        requestIfFirstCall();
                    }
                    scrollListener.setLoading(false);
                }
            });
        }

        private void requestIfFirstCall() {
            recyclerView.postDelayed(() -> {
                recyclerView.smoothScrollToPosition(0);
                int itemCount = layoutManager.getItemCount();
                int lastItemVisible = layoutManager.findLastVisibleItemPosition() + 1;
                if (itemCount == lastItemVisible) {
                    requestMore();
                }
            }, 100);
        }

        @Override
        public void messageAdded(@Nullable Message before, @NonNull Message message) {
            checkChatNotEmptyCallback(true);

            chatWasRead = false;
            int ind = (before == null) ? -1 : adapter.indexOf(before);
            if (ind < 0) {
                boolean fromVisitor = message.getType() == VISITOR || message.getType() == FILE_FROM_VISITOR;
                adapter.add(message);
                adapter.notifyItemInserted(0);

                if ((fromVisitor || isLastVisible()) && isAutoScrollToLastMsgEnabled) {
                    recyclerView.stopScroll();
                    recyclerView.smoothScrollToPosition(0);
                    resetNewMessagesCount();
                } else {
                    if (!fromVisitor) {
                        newMessagesCount++;
                        updateScrollButtonBadge();
                    }
                    scrollContainer.setVisibility(View.VISIBLE);
                }
            } else {
                adapter.add(ind, message);
                adapter.notifyItemInserted(adapter.getItemCount() - ind - 1);
            }
        }

        /**
         * Reset new messages count.
         */
        private void resetNewMessagesCount() {
            newMessagesCount = 0;
            if (scrollCounter != null) {
                scrollCounter.setVisibility(View.GONE);
            }
        }

        /**
         * Update scroll button badge.
         */
        private void updateScrollButtonBadge() {
            if (scrollCounter != null) {
                if (newMessagesCount > 0) {
                    scrollCounter.setText(String.valueOf(newMessagesCount));
                    scrollCounter.setVisibility(View.VISIBLE);
                } else {
                    scrollCounter.setVisibility(View.GONE);
                }
            }
        }

        private void checkChatNotEmptyCallback(boolean added) {
            if (adapter.getItemCount() == 0) {
                messagesCountListener.chatEmpty(!added);
            }
        }

        @Override
        public void messageRemoved(@NonNull Message message) {
            int pos = adapter.indexOf(message);
            if (pos != -1) {
                int recyclerViewPos = adapter.getItemCount() - pos - 1;
                boolean notFromVisitor = message.getType() != VISITOR && message.getType() != FILE_FROM_VISITOR;

                if (notFromVisitor && recyclerViewPos < newMessagesCount) {
                    newMessagesCount = Math.max(0, newMessagesCount - 1);
                    updateScrollButtonBadge();
                }

                adapter.remove(pos, message.getServerSideId());
                adapter.notifyItemRemoved(adapter.getItemCount() - pos);
            }

            checkChatNotEmptyCallback(false);
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = adapter.lastIndexOf(from);
            if (ind != -1) {
                adapter.set(ind, to);
                adapter.notifyItemChanged(adapter.getItemCount() - ind - 1, new Object());
            }
        }

        @Override
        public void allMessagesRemoved() {
            int size = adapter.getItemCount();
            adapter.clear();
            adapter.notifyItemRangeRemoved(0, size);
            if (!requestingMessages) {
                requestMore();
            }
        }

        public void scrollToMessage(Message message) {
            if (message == null) return;

            int index = adapter.indexOf(message);
            if (index != -1) {
                int position = adapter.getItemCount() - index - 1;
                layoutManager.scrollToPosition(position);
                recyclerView.post(() -> adapter.notifyItemChanged(position, PayloadType.SELECT_MESSAGE));
            }
        }

        public void scrollToMessage(String messageId) {
            if (messageId == null) return;

            int index = adapter.indexOf(messageId);
            if (index != -1) {
                int position = adapter.getItemCount() - index - 1;
                layoutManager.scrollToPosition(position);
                recyclerView.post(() -> adapter.notifyItemChanged(position, PayloadType.SELECT_MESSAGE));
            }
        }

        public void scrollToPosition(int position) {
            recyclerView.scrollToPosition(position);
        }

        public void smoothScrollToPosition(int position) {
            recyclerView.scrollToPosition(position);
        }

        private void showMessage(int position) {
            recyclerView.smoothScrollToPosition(position);
        }

        boolean isLastVisible() {
            int position = layoutManager.findFirstVisibleItemPosition();
            return position == 0;
        }
    }
}
