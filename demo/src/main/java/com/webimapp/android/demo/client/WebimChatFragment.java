package com.webimapp.android.demo.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.webimapp.android.demo.client.items.ListItem;
import com.webimapp.android.demo.client.items.WebimMessageListItem;
import com.webimapp.android.demo.client.util.CombinedScrollListener;
import com.webimapp.android.demo.client.util.EndlessScrollListener;
import com.webimapp.android.demo.client.util.NormalTranscriptModeScrollListener;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.WebimError;
import com.webimapp.android.sdk.WebimSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class WebimChatFragment extends Fragment {
    private static final int FILE_SELECT_CODE = 0;
    private WebimSession session;

    private EditText editTextMessage;
    private ListController listController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setWebimSession(WebimSession session) {
        if (this.session != null) {
            throw new IllegalStateException("Webim session is already set");
        }

        this.session = session;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (this.session == null) {
            throw new IllegalStateException("this.session == null; Use setWebimSession before");
        }
        View v = inflater.inflate(R.layout.fragment_webim_chat, container, false);
        initOperatorState(v);
        initListView(v);
        initEditText(v);
        initSendButton(v);
        initAttachFileButton(v);
        ViewCompat.setElevation(v.findViewById(R.id.linLayEnterMessage), 2);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        session.resume();
    }

    @Override
    public void onStop() {
        session.pause();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        session.destroy();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle("");
        }
        super.onDetach();
    }

    private void initOperatorState(final View v) {
        ((AppCompatActivity) getActivity()).setSupportActionBar((Toolbar) v.findViewById(R.id.toolbar));
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        v.findViewById(R.id.action_bar_subtitle).setVisibility(View.GONE);
        session.getStream().setOperatorTypingListener(new MessageStream.OperatorTypingListener() {
            @Override
            public void onOperatorTypingStateChanged(boolean isTyping) {
                if (isTyping) {
                    v.findViewById(R.id.action_bar_subtitle).setVisibility(View.VISIBLE);
                } else {
                    v.findViewById(R.id.action_bar_subtitle).setVisibility(View.GONE);
                }
            }
        });

        setOperatorAvatar(v, session.getStream().getCurrentOperator());

        session.getStream().setCurrentOperatorChangeListener(new MessageStream.CurrentOperatorChangeListener() {
            @Override
            public void onOperatorChanged(@Nullable Operator oldOperator,
                                          @Nullable Operator newOperator) {
                setOperatorAvatar(v, newOperator);
            }
        });
    }

    private void setOperatorAvatar(View v, @Nullable Operator operator) {
        if (operator != null && operator.getAvatarUrl() != null) {
            Glide.with(getContext()).load(operator.getAvatarUrl()).into((ImageView) v.findViewById(R.id.imageAvatar));
            v.findViewById(R.id.imageAvatar).setVisibility(View.VISIBLE);
        } else
            v.findViewById(R.id.imageAvatar).setVisibility(View.GONE);
    }

    private void initListView(View v) {
        final ListView listViewChat = (ListView) v.findViewById(R.id.listViewChat);
        listViewChat.setEmptyView(v.findViewById(R.id.loadingSpinner));
        listController = ListController.install(getContext(), listViewChat, session.getStream(), v);
    }

    private void initEditText(View v) {
        editTextMessage = (EditText) v.findViewById(R.id.editTextChatMessage);
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String draft = editable.toString();
                session.getStream().setVisitorTyping(draft.isEmpty() ? null : draft);
            }
        });
    }

    private void initSendButton(View v) {
        ImageButton sendButton = (ImageButton) v.findViewById(R.id.imageButtonSendMessage);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                message = message.trim();
                if (!message.isEmpty()) {
                    if (BuildConfig.DEBUG && message.equals("##CLOSE")) {
                        session.getStream().closeChat();
                    } else if (BuildConfig.DEBUG && message.equals("##OPEN")) {
                        session.getStream().startChat();
                    } else {
                        session.getStream().sendMessage(message);
                    }
                }
            }
        });
    }

    private void initAttachFileButton(View v) {
        ImageButton attachButton = (ImageButton) v.findViewById(R.id.imageButtonAttachFile);
        attachButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    startActivityForResult(
                            Intent.createChooser(intent,
                                    getContext().getString(R.string.file_chooser_title)), FILE_SELECT_CODE);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(getContext(), getContext().getString(R.string.file_chooser_not_found),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = data.getData();
                    String mime = getActivity().getContentResolver().getType(uri);
                    String extension = mime == null
                            ? null
                            : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    String name = extension == null
                            ? null
                            : uri.getLastPathSegment() + "." + extension;
                    File file = null;
                    try {
                        InputStream inp = getActivity().getContentResolver().openInputStream(uri);
                        if (inp == null) {
                            file = null;
                        } else {
                            file = File.createTempFile("webim",
                                    extension, getActivity().getCacheDir());
                            writeFully(file, inp);
                        }
                    } catch (IOException e) {
                        Log.e("WEBIM", "failed to copy selected file", e);
                        file.delete();
                        file = null;
                    }

                    if (file != null && name != null) {
                        final File fileToUpload = file;
                        session.getStream().sendFile(fileToUpload,
                                name, mime, new MessageStream.SendFileCallback() {
                            @Override
                            public void onProgress(@NonNull Message.Id id, long sentBytes) {

                            }

                            @Override
                            public void onSuccess(@NonNull Message.Id id) {
                                fileToUpload.delete();
                            }

                            @Override
                            public void onFailure(@NonNull Message.Id id,
                                                  @NonNull WebimError<SendFileError> error) {
                                fileToUpload.delete();
                                Toast.makeText(getContext(), getContext().getString(
                                        error.getErrorType() == SendFileError.FILE_SIZE_EXCEEDED
                                                ? R.string.file_upload_failed_size
                                                : R.string.file_upload_failed_type),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                        break;
                    }
                }
                if (resultCode != Activity.RESULT_CANCELED) {
                    Toast.makeText(getContext(), getContext().getString(R.string.file_selection_failed),
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static void writeFully(@NonNull File to, @NonNull InputStream from) throws IOException {
        byte[] buffer = new byte[4096];
        OutputStream out = null;
        try {
            out = new FileOutputStream(to);
            for (int read; (read = from.read(buffer)) != -1; )
                out.write(buffer, 0, read);
        } finally {
            from.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private static class ListController implements MessageListener {
        private static final int MESSAGES_PER_REQUEST = 25;
        private final MessageTracker tracker;
        private final ListView listViewChat;
        private final MessagesAdapter adapter;
        private final EndlessScrollListener scrollListener;
        private final List<ListItem> messages = new ArrayList<>();

        private boolean requestingMessages;

        public static ListController install(Context context,
                                             ListView listViewChat,
                                             MessageStream chat,
                                             View view) {
            return new ListController(context, chat, listViewChat, view);
        }

        private ListController(Context context,
                               MessageStream stream,
                               final ListView listViewChat,
                               View view) {
            this.listViewChat = listViewChat;
            listViewChat.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            listViewChat.setStackFromBottom(true);
            this.adapter = new MessagesAdapter(context,
                    messages, new ShowRateDialogOnAvatarClickListener(context, stream));
            listViewChat.setAdapter(adapter);
            tracker = stream.newMessageTracker(this);
            final FloatingActionButton button = (FloatingActionButton) view.findViewById(R.id.downButton);
            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }
            };
            scrollListener.setLoading(true);
            scrollListener.setButton(button);
            requestMore();
            listViewChat.setOnScrollListener(new CombinedScrollListener(new NormalTranscriptModeScrollListener(), scrollListener));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    button.setVisibility(View.GONE);
                    listViewChat.setSelection(listViewChat.getMaxScrollAmount());
                }
            });
        }

        private void requestMore() {
            requestingMessages = true;
            tracker.getNextMessages(MESSAGES_PER_REQUEST, new MessageTracker.GetMessagesCallback() {
                @Override
                public void receive(@NonNull List<? extends Message> received) {
                    requestingMessages = false;
                    if (received.size() != 0) {
                        scrollListener.setLoading(false);
                        // Scroll position fix
                        int index = listViewChat.getFirstVisiblePosition();
                        View v = listViewChat.getChildAt(listViewChat.getHeaderViewsCount());
                        int topOffset = (v == null) ? 0 : v.getTop();
                        List<ListItem> items = new ArrayList<>(received.size());
                        for (Message msg : received)
                            items.add(new WebimMessageListItem(msg));
                        messages.addAll(0, items);
                        adapter.notifyDataSetChanged();
                        listViewChat.setSelectionFromTop(index + received.size(), topOffset);
                    } else {
                        listViewChat.getEmptyView().setVisibility(View.GONE);
                        listViewChat.setEmptyView(((ViewGroup) listViewChat.getParent())
                                .findViewById(R.id.emptyHistoryView));
                    }
                }
            });
        }

        @Override
        public void messageAdded(@Nullable Message before, @NonNull Message message) {
            int ind = before == null
                    ? messages.size()
                    : messages.lastIndexOf(new WebimMessageListItem(before));
            if (ind < 0) {
                messages.add(new WebimMessageListItem(message));
            }
            else {
                messages.add(ind, new WebimMessageListItem(message));
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void messageRemoved(@NonNull Message message) {
            messages.remove(new WebimMessageListItem(message));
            adapter.notifyDataSetChanged();
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = messages.lastIndexOf(new WebimMessageListItem(from));
            if (ind != -1) {
                messages.set(ind, new WebimMessageListItem(to));
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void allMessagesRemoved() {
            messages.clear();
            adapter.notifyDataSetChanged();
            if (!requestingMessages) {
                requestMore();
            }
        }
    }

    private static class ShowRateDialogOnAvatarClickListener implements View.OnClickListener {
        private final Context context;
        private final MessageStream stream;

        private ShowRateDialogOnAvatarClickListener(Context context, MessageStream stream) {
            this.context = context;
            this.stream = stream;
        }

        @Override
        public void onClick(View view) {
            Message item = (Message) view.getTag(R.id.webimMessage);
            if (item != null) {
                showRateDialog(item);
            }
        }

        private void showRateDialog(Message item) {
            final Operator.Id operatorId = item.getOperatorId();
            if (operatorId == null) {
                return;
            }
            showRateDialog(operatorId);
        }

        private void showRateDialog(@NonNull final Operator.Id operatorId) {
            int rating = stream.getLastOperatorRating(operatorId);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.rate_operator_title);
            View view = View.inflate(context, R.layout.rating_bar, null);
            final RatingBar bar = (RatingBar) view.findViewById(R.id.ratingBar);
            if (rating != 0) {
                bar.setRating(rating);
            }
            Button button = (Button) view.findViewById(R.id.ratingBarButton);
            builder.setView(view);
            final AlertDialog dialog = builder.create();
            dialog.show();
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (bar.getRating() != 0) {
                        stream.rateOperator(operatorId, (int) bar.getRating());
                    }
                }
            });
        }
    }
}
