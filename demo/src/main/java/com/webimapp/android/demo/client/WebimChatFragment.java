package com.webimapp.android.demo.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.webimapp.android.demo.client.util.EndlessScrollListener;
import com.webimapp.android.sdk.Department;
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

    private final int menuRateOperator = Menu.FIRST;
    private final int menuCloseChat = Menu.FIRST + 1;
    private MenuItem rateOperatorItem;
    private MenuItem closeChatItem;

    private WebimSession session;
    private ListController listController;
    private Message inEdit = null;

    private EditText editTextMessage;
    private ImageButton sendButton;
    private ImageButton editButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        if (this.session == null) {
            throw new IllegalStateException("this.session == null; Use setWebimSession before");
        }
        View rootView = inflater.inflate(R.layout.fragment_webim_chat, container, false);

        initSessionStreamListeners(rootView);
        initOperatorState(rootView);
        initChatView(rootView);
        initEditText(rootView);
        initSendButton(rootView);
        initEditButton(rootView);
        initAttachFileButton(rootView);

        ViewCompat.setElevation(rootView.findViewById(R.id.linLayEnterMessage), 2);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        session.resume();
        session.getStream().startChat();
        session.getStream().setChatRead();
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

    private void initSessionStreamListeners(final View rootView) {
        session.getStream().setCurrentOperatorChangeListener(
                new MessageStream.CurrentOperatorChangeListener() {
                    @Override
                    public void onOperatorChanged(@Nullable Operator oldOperator,
                                                  @Nullable Operator newOperator) {
                        if (rateOperatorItem != null) {
                            rateOperatorItem.setEnabled(newOperator != null);
                        }
                        setOperatorAvatar(rootView, newOperator);

                        TextView operatorNameView = rootView.findViewById(R.id.action_bar_subtitle);
                        String operatorName = newOperator == null
                                ? getString(R.string.no_operator)
                                : newOperator.getName();
                        operatorNameView.setText(getString(R.string.operator_name, operatorName));
                    }
                });

        session.getStream().setVisitSessionStateListener(new MessageStream.VisitSessionStateListener() {
            @Override
            public void onStateChange(@NonNull MessageStream.VisitSessionState previousState,
                                      @NonNull MessageStream.VisitSessionState newState) {
                if (newState == MessageStream.VisitSessionState.DEPARTMENT_SELECTION) {
                    final List<Department> departmentList = session.getStream().getDepartmentList();
                    if (departmentList != null) {
                        final ArrayList<String> departmentNames = new ArrayList<>();
                        for (Department department : departmentList) {
                            departmentNames.add(department.getName());
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(rootView.getContext());
                        builder.setTitle(R.string.choose_department)
                                .setItems(
                                        departmentNames.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                session.getStream().startChatWithDepartmentKey(
                                                        departmentList.get(which).getKey());
                                            }
                                        })
                                .setNegativeButton(
                                        R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int id) {
                                            }
                                        });

                        final AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
            }
        });

        session.getStream().setChatStateListener(new MessageStream.ChatStateListener() {
            @Override
            public void onStateChange(@NonNull MessageStream.ChatState oldState,
                                      @NonNull MessageStream.ChatState newState) {
                if (closeChatItem != null) {
                    closeChatItem.setEnabled(newState != MessageStream.ChatState.NONE
                            && newState != MessageStream.ChatState.CLOSED_BY_VISITOR
                            && newState != MessageStream.ChatState.UNKNOWN);
                }

                if (newState == MessageStream.ChatState.CLOSED_BY_OPERATOR
                        || newState == MessageStream.ChatState.CLOSED_BY_VISITOR) {
                    showRateOperatorDialog();
                }
            }
        });
    }

    private void initOperatorState(final View rootView) {
        ((AppCompatActivity) getActivity())
                .setSupportActionBar((Toolbar) rootView.findViewById(R.id.toolbar));
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        final TextView typingView = rootView.findViewById(R.id.action_bar_subtitle);
        Operator currentOp = session.getStream().getCurrentOperator();
        String operatorName = currentOp == null
                ? getString(R.string.no_operator)
                : currentOp.getName();
        typingView.setText(getString(R.string.operator_name, operatorName));

        session.getStream().setOperatorTypingListener(new MessageStream.OperatorTypingListener() {
            @Override
            public void onOperatorTypingStateChanged(boolean isTyping) {
                Operator currentOp = session.getStream().getCurrentOperator();
                String operatorName = currentOp == null
                        ? getString(R.string.no_operator)
                        : currentOp.getName();
                typingView.setText(isTyping
                        ? getString(R.string.operator_typing, operatorName)
                        : getString(R.string.operator_name, operatorName));
            }
        });

        setOperatorAvatar(rootView, session.getStream().getCurrentOperator());
    }

    public void setWebimSession(WebimSession session) {
        if (this.session != null) {
            throw new IllegalStateException("Webim session is already set");
        }
        this.session = session;
    }

    private void setOperatorAvatar(View v, @Nullable Operator operator) {
        if (operator != null) {
            if (operator.getAvatarUrl() != null) {
                Glide.with(getContext())
                        .load(operator.getAvatarUrl())
                        .into((ImageView) v.findViewById(R.id.sender_photo));
            } else {
                ((ImageView) v.findViewById(R.id.sender_photo)).setImageDrawable(
                        getContext().getResources()
                                .getDrawable(R.drawable.default_operator_avatar_40x40));
            }
            v.findViewById(R.id.sender_photo).setVisibility(View.VISIBLE);
        } else {
            v.findViewById(R.id.sender_photo).setVisibility(View.GONE);
        }
    }

    private void initChatView(View rootView) {
        ProgressBar progressBar = rootView.findViewById(R.id.loading_spinner);
        progressBar.setVisibility(View.GONE);

        RecyclerView recyclerView = rootView.findViewById(R.id.chat_recycler_view);
        recyclerView.setVisibility(View.VISIBLE);
        listController =
                ListController.install(this, recyclerView, progressBar,
                        session.getStream(), rootView);
    }

    private void initEditText(View rootView) {
        editTextMessage = rootView.findViewById(R.id.editTextChatMessage);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        rateOperatorItem = menu.add(menu.FIRST,
                menuRateOperator,
                menuRateOperator,
                R.string.rate_operator);
        if (rateOperatorItem != null) {
            rateOperatorItem.setEnabled(session.getStream().getCurrentOperator() != null);
        }
        closeChatItem = menu.add(menu.FIRST, menuCloseChat, menuCloseChat, R.string.close_chat);
        if (closeChatItem != null) {
            MessageStream.ChatState chatState = session.getStream().getChatState();
            closeChatItem.setEnabled(chatState != MessageStream.ChatState.NONE
                    && chatState != MessageStream.ChatState.CLOSED_BY_VISITOR
                    && chatState != MessageStream.ChatState.UNKNOWN);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case menuRateOperator:
                showRateOperatorDialog();
                break;
            case menuCloseChat:
                showCloseChatDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRateOperatorDialog() {
        Operator operator = session.getStream().getCurrentOperator();
        if (operator != null) {
            final Operator.Id operatorId = operator.getId();
            int rating = session.getStream().getLastOperatorRating(operatorId);
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.rate_operator_title);
            View view = View.inflate(getContext(), R.layout.rating_bar, null);
            final RatingBar bar = view.findViewById(R.id.ratingBar);
            if (rating != 0) {
                bar.setRating(rating);
            }
            Button button = view.findViewById(R.id.ratingBarButton);
            builder.setView(view);
            final AlertDialog dialog = builder.create();
            dialog.show();
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (bar.getRating() != 0) {
                        session.getStream().rateOperator(
                                operatorId, (int) bar.getRating(), null
                        );
                    }
                }
            });
        }
    }

    private void showCloseChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(R.string.alert_close_chat)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        session.getStream().closeChat();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void initSendButton(View v) {
        sendButton = v.findViewById(R.id.imageButtonSendMessage);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                message = message.trim();
                if (!message.isEmpty()) {
                    if (BuildConfig.DEBUG && message.equals("##OPEN")) {
                        session.getStream().startChat();
                    } else {
                        session.getStream().sendMessage(message);
                    }
                }
            }
        });
    }

    private void initEditButton(View v) {
        editButton = v.findViewById(R.id.imageButtonAcceptChanges);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inEdit == null) {
                    return;
                }

                String newText = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                newText = newText.trim();
                if (!newText.isEmpty()) {
                    session.getStream().editMessage(inEdit, newText, null);
                } else {
                    session.getStream().deleteMessage(inEdit, null);
                }
                editButton.setVisibility(View.GONE);
                sendButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initAttachFileButton(View rootView) {
        ImageButton attachButton = rootView.findViewById(R.id.imageButtonAttachFile);
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
                                        String msg;
                                        switch (error.getErrorType()) {
                                            case FILE_TYPE_NOT_ALLOWED:
                                                msg = getContext().getString(
                                                        R.string.file_upload_failed_type);
                                                break;
                                            case FILE_SIZE_EXCEEDED:
                                                msg = getContext().getString(
                                                        R.string.file_upload_failed_size);
                                                break;
                                            case UPLOADED_FILE_NOT_FOUND:
                                            default:
                                                msg = getContext().getString(
                                                        R.string.file_upload_failed_unknown);
                                        }
                                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
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
            for (int read; (read = from.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
        } finally {
            from.close();
            if (out != null) {
                out.close();
            }
        }
    }

    void onEditAction(Message message) {
        inEdit = message;
        sendButton.setVisibility(View.GONE);
        editButton.setVisibility(View.VISIBLE);
        editTextMessage.setText(message.getText());
        editTextMessage.setSelection(message.getText().length());
    }

    void onDeleteMessageAction(Message message) {
        session.getStream().deleteMessage(message, null);
    }

    private static class ListController implements MessageListener {
        private static final int MESSAGES_PER_REQUEST = 25;
        private final MessageTracker tracker;
        private final RecyclerView recyclerView;
        private final ProgressBar progressBar;
        private final MessagesAdapter adapter;
        private final LinearLayoutManager layoutManager;
        private final EndlessScrollListener scrollListener;

        private boolean requestingMessages;

        static ListController install(WebimChatFragment webimChatFragment,
                                             RecyclerView recyclerView,
                                             ProgressBar progressBar,
                                             MessageStream messageStream,
                                             View rootView) {
            return new ListController(webimChatFragment,
                    recyclerView,
                    progressBar,
                    messageStream,
                    rootView);
        }

        private ListController(WebimChatFragment webimChatFragment,
                               final RecyclerView recyclerView,
                               final ProgressBar progressBar,
                               final MessageStream messageStream,
                               View view) {
            this.recyclerView = recyclerView;
            this.progressBar = progressBar;

            this.layoutManager = new LinearLayoutManager(
                    webimChatFragment.getContext(), LinearLayoutManager.VERTICAL, true);
            this.layoutManager.setStackFromEnd(false);
            this.recyclerView.setLayoutManager(layoutManager);
            this.adapter = new MessagesAdapter(webimChatFragment);

            this.recyclerView.setAdapter(this.adapter);

            this.tracker = messageStream.newMessageTracker(this);

            final FloatingActionButton downButton = view.findViewById(R.id.downButton);
            downButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    downButton.setVisibility(View.GONE);
                    recyclerView.smoothScrollToPosition(0);
                }
            });

            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }
            };
            scrollListener.setLoading(true);
            scrollListener.setDownButton(downButton);
            scrollListener.setAdapter(adapter);
            recyclerView.addOnScrollListener(scrollListener);
            requestMore(true);
        }

        private void requestMore() {
            requestMore(false);
        }

        private void requestMore(final boolean flage) {
            requestingMessages = true;
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tracker.getNextMessages(MESSAGES_PER_REQUEST, new MessageTracker.GetMessagesCallback() {
                @Override
                public void receive(@NonNull List<? extends Message> received) {
                    requestingMessages = false;
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    if (received.size() != 0) {
                        adapter.addAll(0, received);
                        adapter.notifyItemRangeInserted(adapter.getItemCount() - 1,
                                received.size());

                        if (flage) {
                            recyclerView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    recyclerView.smoothScrollToPosition(0);
                                }
                            }, 100);
                        }
                        scrollListener.setLoading(false);
                    }
                }
            });
        }

        @Override
        public void messageAdded(@Nullable Message before, @NonNull Message message) {
            int ind = (before == null) ? 0 : adapter.indexOf(before);
            if (ind <= 0) {
                adapter.add(message);
                adapter.notifyItemInserted(0);
                recyclerView.smoothScrollToPosition(0);
            } else {
                adapter.add(ind, message);
                adapter.notifyItemInserted(adapter.getItemCount() - ind - 1);
            }
        }

        @Override
        public void messageRemoved(@NonNull Message message) {
            int pos = adapter.indexOf(message);
            if (pos != -1) {
                adapter.remove(pos);
                adapter.notifyItemRemoved(adapter.getItemCount() - pos);
            }
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = adapter.lastIndexOf(from);
            if (ind != -1) {
                adapter.set(ind, to);
                adapter.notifyItemChanged(adapter.getItemCount() - ind - 1);
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
    }
}
