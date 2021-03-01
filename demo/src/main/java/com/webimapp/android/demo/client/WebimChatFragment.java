package com.webimapp.android.demo.client;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.webimapp.android.demo.client.util.DepartmentItemSelectedCallback;
import com.webimapp.android.demo.client.util.DynamicContextDialog;
import com.webimapp.android.demo.client.util.EndlessScrollListener;
import com.webimapp.android.demo.client.util.MenuController;
import com.webimapp.android.demo.client.util.SurveyDialog;
import com.webimapp.android.sdk.Department;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.Survey;
import com.webimapp.android.sdk.WebimError;
import com.webimapp.android.sdk.WebimSession;
import com.webimapp.android.sdk.impl.items.DepartmentItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static com.webimapp.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static com.webimapp.android.sdk.Message.Type.VISITOR;

public class WebimChatFragment extends Fragment {
    private static final int FILE_SELECT_CODE = 0;

    private WebimSession session;
    private ListController listController;
    private Message inEdit = null;
    private Message quotedMessage = null;

    private EditText editTextMessage;
    private ImageButton sendButton;
    private ImageButton editButton;

    private LinearLayout editingLayout;
    private TextView textEditingMessage;
    private int editingMessagePosition;

    private LinearLayout replyLayout;
    private TextView textSenderName;
    private TextView textReplyMessage;
    private int replyMessagePosition;
    private ImageView replyThumbnail;
    private TextView textReplyId;

    private RelativeLayout chatMenuLayout;
    private RelativeLayout rateOperatorLayout;
    private LinearLayout chatMenuBackground;
    private ImageButton chatMenuButton;

    private RatingBar ratingBar;
    private Button ratingButton;

    private MenuController menuController;

    private AlertDialog ratingDialog;
    private SurveyDialog surveyDialog;
    private DepartmentDialog departmentDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
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
        initEditingLayout(rootView);
        initDeleteEditing(rootView);
        initEditingMessageButton(rootView);
        initChatMenu(rootView);
        initReplyLayout(rootView);
        initDeleteReply(rootView);
        initReplyMessageButton(rootView);
        initOperatorRateDialog();
        initSurveyDialog();

        menuController = new MenuController(this);

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
        if (getActivity() != null) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle("");
            }
        }
        super.onDetach();
    }

    private void initSessionStreamListeners(final View rootView) {
        session.getStream().setCurrentOperatorChangeListener(
                new MessageStream.CurrentOperatorChangeListener() {
                    @Override
                    public void onOperatorChanged(@Nullable Operator oldOperator,
                                                  @Nullable Operator newOperator) {
                        settingRateOperatorLayout(
                                rootView,
                                newOperator != null);

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
                switch (newState) {
                    case DEPARTMENT_SELECTION:
                        List<Department> departments = session.getStream().getDepartmentList();
                        if (departmentDialog == null) {
                            openDepartmentDialog(departments);
                        } else {
                            departmentDialog.dismiss();
                            departmentDialog.setDepartmentNames(getDepartmentsNames(departments));
                            departmentDialog.show(getChildFragmentManager(), DepartmentDialog.DEPARTMENT_DIALOG_TAG);
                        }
                        break;
                    case IDLE_AFTER_CHAT:
                        if (departmentDialog != null) {
                            departmentDialog.dismiss();
                        }
                        break;
                }
            }
        });

        session.getStream().setChatStateListener(new MessageStream.ChatStateListener() {
            @Override
            public void onStateChange(@NonNull MessageStream.ChatState oldState,
                                      @NonNull MessageStream.ChatState newState) {
                switch (newState) {
                    case CLOSED_BY_OPERATOR:
                    case CLOSED_BY_VISITOR:
                        showRateOperatorDialog();
                        hideEditLayout();
                        break;
                    case NONE:
                        hideEditLayout();
                        if (ratingDialog.isShowing()) {
                            ratingDialog.dismiss();
                        }
                        break;
                }
            }
        });

        session.getStream().setSurveyListener(new MessageStream.SurveyListener() {
            @Override
            public void onSurvey(Survey survey) {
                if (ratingDialog.isShowing()) {
                    ratingDialog.dismiss();
                }
                if (!surveyDialog.isVisible()) {
                    surveyDialog.showNow(getChildFragmentManager(), "surveyDialog");
                }
            }

            @Override
            public void onNextQuestion(Survey.Question question) {
                surveyDialog.setCurrentQuestion(question);
            }

            @Override
            public void onSurveyCancelled() {
                if (surveyDialog.isVisible()) {
                    surveyDialog.dismiss();
                }
                showToast(getString(R.string.survey_finish_message), Toast.LENGTH_SHORT);
            }
        });
    }

    private void openDepartmentDialog(List<Department> departmentList) {
        if (departmentList != null) {
            departmentDialog =
                new DepartmentDialog(new DepartmentItemSelectedCallback() {
                    @Override
                    public void departmentItemSelected(int departmentPosition) {
                        List<Department> departmentList = session.getStream().getDepartmentList();
                        if (departmentList != null && !departmentList.isEmpty()) {
                            String selectedDepartment = departmentList.get(departmentPosition).getKey();
                            session.getStream().startChatWithDepartmentKey(selectedDepartment);
                        }
                    }

                    @Override
                    public void onBackPressed() {
                        requireActivity().onBackPressed();
                    }
                });
            departmentDialog.setDepartmentNames(getDepartmentsNames(departmentList));
            departmentDialog.setCancelable(false);
            departmentDialog.show(getChildFragmentManager(), DepartmentDialog.DEPARTMENT_DIALOG_TAG);
        }
    }

    private List<String> getDepartmentsNames(List<Department> departmentList) {
        final List<String> departmentNames = new ArrayList<>();
        for (Department department : departmentList) {
            departmentNames.add(department.getName());
        }
        return departmentNames;
    }

    private void initOperatorState(final View rootView) {
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity())
                    .setSupportActionBar((Toolbar) rootView.findViewById(R.id.toolbar));
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            }
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
                ImageView imageView = rootView.findViewById(R.id.image_typing);
                imageView.setBackgroundResource(R.drawable.typing_animation);
                AnimationDrawable animationDrawable = (AnimationDrawable) imageView.getBackground();
                Operator currentOp = session.getStream().getCurrentOperator();
                String operatorName = currentOp == null
                        ? getString(R.string.no_operator)
                        : currentOp.getName();
                if (isTyping) {
                    typingView.setText(getString(R.string.operator_typing));
                    typingView.setTextColor(
                            rootView.getResources().getColor(R.color.colorTexWhenTyping));
                    imageView.setVisibility(View.VISIBLE);
                    animationDrawable.start();
                } else {
                    typingView.setText(getString(R.string.operator_name, operatorName));
                    typingView.setTextColor(
                            rootView.getResources().getColor(R.color.white));
                    imageView.setVisibility(View.GONE);
                    animationDrawable.stop();
                }
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
                if (getContext() != null) {
                    ((ImageView) v.findViewById(R.id.sender_photo)).setImageDrawable(
                            getContext().getResources()
                                    .getDrawable(R.drawable.default_operator_avatar));
                }
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
                if (chatMenuLayout.getVisibility() == View.VISIBLE) {
                    hideChatMenu();
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String draft = editable.toString().trim();
                if (draft.isEmpty()) {
                    editButton.setAlpha(0.5f);
                    editButton.setEnabled(false);
                    session.getStream().setVisitorTyping(null);
                } else {
                    editButton.setAlpha(1f);
                    editButton.setEnabled(true);
                    session.getStream().setVisitorTyping(draft);
                }
            }
        });
        editTextMessage.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && (chatMenuLayout.getVisibility() == View.VISIBLE)) {
                    hideChatMenu();
                }
            }
        });
    }

    private void initSendButton(View rootView) {
        sendButton = rootView.findViewById(R.id.imageButtonSendMessage);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                message = message.trim();
                if (!message.isEmpty()) {
                    if (BuildConfig.DEBUG && message.equals("##OPEN")) {
                        session.getStream().startChat();
                    } else {
                        if (replyLayout.getVisibility() == View.GONE) {
                            session.getStream().sendMessage(message);
                        } else {
                            replyLayout.setVisibility(View.GONE);
                            session.getStream().replyMessage(
                                    message,
                                    quotedMessage);
                        }
                    }
                    if (chatMenuLayout.getVisibility() == View.VISIBLE) {
                        hideChatMenu();
                    }
                }
            }
        });
    }

    private void initEditingLayout(View rootView) {
        editingLayout = rootView.findViewById(R.id.linLayEditMessage);
        textEditingMessage = rootView.findViewById(R.id.textViewEditText);
        LinearLayout editTextLayout = rootView.findViewById(R.id.linLayEditBody);
        editTextLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listController.showMessage(editingMessagePosition);
            }
        });
        editingLayout.setVisibility(View.GONE);
    }

    private void initDeleteEditing(View rootView) {
        ImageView deleteEditButton = rootView.findViewById(R.id.imageButtonEditDelete);
        deleteEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideEditLayout();
                editTextMessage.getText().clear();
            }
        });
    }

    private void initEditButton(View rootView) {
        editButton = rootView.findViewById(R.id.imageButtonAcceptChanges);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inEdit == null) {
                    return;
                }

                String newText = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                newText = newText.trim();
                if (newText.isEmpty()) {
                    showToast(getString(R.string.failed_send_empty_message), Toast.LENGTH_SHORT);
                } else if (!newText.equals(inEdit.getText().trim())) {
                    session.getStream().editMessage(inEdit, newText, null);
                }
                hideEditLayout();
            }
        });
    }

    private void initEditingMessageButton(final View rootView) {
        ImageView editButton = rootView.findViewById(R.id.imageButtonEditMessage);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listController.showMessage(editingMessagePosition);
            }
        });
    }

    private void initChatMenu(final View rootView) {
        chatMenuLayout = rootView.findViewById(R.id.chat_menu);
        chatMenuBackground = rootView.findViewById(R.id.chat_menu_background);
        chatMenuButton = rootView.findViewById(R.id.imageButtonChatMenu);

        chatMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (chatMenuLayout.getVisibility() == View.GONE) {
                    Animation animationScaleUp =
                            AnimationUtils.loadAnimation(getContext(), R.anim.scale_up);
                    animationScaleUp.setAnimationListener(new Animation.AnimationListener(){
                        @Override
                        public void onAnimationStart(Animation arg0) {
                        }
                        @Override
                        public void onAnimationRepeat(Animation arg0) {
                        }
                        @Override
                        public void onAnimationEnd(Animation arg0) {
                            LinearLayout chatMenuBackground =
                                    rootView.findViewById(R.id.chat_menu_background);
                            Animation animationAlfaHide =
                                    AnimationUtils.loadAnimation(getContext(), R.anim.alfa_hide);
                            chatMenuBackground.startAnimation(animationAlfaHide);
                            chatMenuBackground.setVisibility(View.VISIBLE);
                        }
                    });
                    chatMenuLayout.startAnimation(animationScaleUp);
                    chatMenuLayout.setVisibility(View.VISIBLE);
                    Animation animationRotateShow =
                            AnimationUtils.loadAnimation(getContext(), R.anim.rotate_show);
                    chatMenuButton.startAnimation(animationRotateShow);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        replyLayout.setVisibility(View.GONE);
                    }
                } else {
                    hideChatMenu();
                }
            }
        });
        RelativeLayout newAttachmentLayout = rootView.findViewById(R.id.relLay_new_attachment);
        rateOperatorLayout = rootView.findViewById(R.id.relLay_rate_operator);
        chatMenuLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideChatMenu();
            }
        });
        newAttachmentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideChatMenu();
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    if (getContext() != null) {
                        startActivityForResult(
                                Intent.createChooser(
                                        intent, getContext().getString(R.string.file_chooser_title)),
                                FILE_SELECT_CODE);
                    }
                } catch (android.content.ActivityNotFoundException e) {
                    if (getContext() != null) {
                        showToast(getContext().getString(R.string.file_chooser_not_found), Toast.LENGTH_SHORT);
                    }
                }
            }
        });
        settingRateOperatorLayout(
                rootView,
                session.getStream().getCurrentOperator() != null);
    }

    private void settingRateOperatorLayout(final View rootView, final boolean operatorIsAvailable) {
        TextView textRateOperator = rootView.findViewById(R.id.text_rate_operator);
        if (operatorIsAvailable) {
            rateOperatorLayout.setEnabled(true);
            textRateOperator.setTextColor(rootView.getResources().getColor(R.color.colorText));
        } else {
            rateOperatorLayout.setEnabled(false);
            textRateOperator.setTextColor(rootView.getResources().getColor(R.color.colorHintText));
        }
        rateOperatorLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (operatorIsAvailable) {
                    hideChatMenu();
                    showRateOperatorDialog();
                }
            }
        });
    }

    private void showRateOperatorDialog() {
        Operator operator = session.getStream().getCurrentOperator();
        if (operator != null) {
            final Operator.Id operatorId = operator.getId();
            int rating = session.getStream().getLastOperatorRating(operatorId);
            ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
                @Override
                public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                    ratingButton.setEnabled(rating != 0);
                }
            });
            ratingBar.setRating(rating);
            ratingButton.setEnabled(rating != 0);
            ratingDialog.show();
            ratingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ratingBar.getRating() != 0) {
                        ratingDialog.dismiss();
                        showToast(getString(R.string.rate_operator_rating_sent), Toast.LENGTH_LONG);
                        session.getStream().rateOperator(
                                operatorId, (int) ratingBar.getRating(), null
                        );
                    } else {
                        showToast(getString(R.string.rate_operator_rating_empty), Toast.LENGTH_LONG);
                    }
                }
            });
        }
    }

    private void showToast(String messageToast, int lengthToast) {
        Toast.makeText(getContext(), messageToast, lengthToast).show();
    }

    public void hideChatMenu() {
        chatMenuBackground.setVisibility(View.GONE);

        Animation animationScaleDown = AnimationUtils.loadAnimation(getContext(), R.anim.scale_down);
        chatMenuLayout.startAnimation(animationScaleDown);

        Animation animationRotateHide = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
        chatMenuButton.startAnimation(animationRotateHide);

        chatMenuLayout.setVisibility(View.GONE);
    }

    public boolean isChatMenuVisible() {
        return chatMenuLayout.getVisibility() == View.VISIBLE;
    }

    private void initReplyLayout(View rootView) {
        replyLayout = rootView.findViewById(R.id.linLayReplyMessage);
        textSenderName = rootView.findViewById(R.id.textViewSenderName);
        textReplyMessage = rootView.findViewById(R.id.textViewReplyText);
        replyThumbnail = rootView.findViewById(R.id.replyThumbnail);
        textReplyId = rootView.findViewById(R.id.quote_Id);

        ViewGroup replyTextLayout  = rootView.findViewById(R.id.linLayReplyBody);
        replyTextLayout.setOnClickListener(view -> listController.showMessage(replyMessagePosition));
        replyLayout.setVisibility(View.GONE);
    }

    private  void initDeleteReply(View rootView) {
        ImageView deleteReplyButton = rootView.findViewById(R.id.imageButtonReplyDelete);
        deleteReplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replyLayout.setVisibility(View.GONE);
            }
        });
    }

    private void initReplyMessageButton(View rootView) {
        ImageView replyButton = rootView.findViewById(R.id.imageButtonReplyMessage);
        replyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listController.showMessage(replyMessagePosition);
            }
        });
    }

    private void initOperatorRateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.rate_operator_title);
        View view = View.inflate(getContext(), R.layout.rating_bar, null);
        ratingBar = view.findViewById(R.id.ratingBar);
        ratingButton = view.findViewById(R.id.ratingBarButton);
        builder.setView(view);
        ratingDialog = builder.create();
    }

    private void initSurveyDialog() {
        surveyDialog = new SurveyDialog();
        surveyDialog.setAnswerListener(answer -> session.getStream().sendSurveyAnswer(answer, null));
        surveyDialog.setCancelListener(() -> session.getStream().closeSurvey(null));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null && getActivity() != null) {
                    String mime = null;
                    String contentScheme = "content";
                    String fileScheme = "file";

                    if (uri.getScheme().equals(contentScheme)) {
                        mime = getActivity().getContentResolver().getType(uri);
                    } else if (uri.getScheme().equals(fileScheme)) {
                        FileNameMap fileNameMap = URLConnection.getFileNameMap();
                        mime = fileNameMap.getContentTypeFor(uri.getLastPathSegment());
                    }
                    String extension = mime == null
                        ? null
                        : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    String name = extension == null
                        ? null
                        : uri.getLastPathSegment() + "." + extension;
                    File file = null;
                    try {
                        InputStream inp = getActivity().getContentResolver().openInputStream(uri);
                        if (inp != null) {
                            file = File.createTempFile("webim",
                                extension, getActivity().getCacheDir());
                            writeFully(file, inp);
                            Cursor cursor = getActivity().getContentResolver().query(
                                uri,
                                null,
                                null,
                                null,
                                null);
                            if (cursor != null && cursor.moveToFirst()) {
                                name = cursor.getString(
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                                cursor.close();
                            }
                        }
                    } catch (IOException e) {
                        Log.e("WEBIM", "failed to copy selected file", e);
                        if (file != null) {
                            boolean fileDeleted = file.delete();
                            if (!fileDeleted) {
                                Log.w("WEBIM", "failed to deleted file " + file.getName());
                            }
                            file = null;
                        }
                    }

                    if (file != null && name != null && mime != null) {
                        final File fileToUpload = file;
                        session.getStream().sendFile(
                            fileToUpload,
                            name,
                            mime,
                            new MessageStream.SendFileCallback() {
                                @Override
                                public void onProgress(@NonNull Message.Id id, long sentBytes) {

                                }

                                @Override
                                public void onSuccess(@NonNull Message.Id id) {
                                    deleteFile(fileToUpload);
                                }

                                @Override
                                public void onFailure(@NonNull Message.Id id,
                                                      @NonNull WebimError<SendFileError> error) {
                                    deleteFile(fileToUpload);
                                    if (getContext() != null) {
                                        String message;
                                        switch (error.getErrorType()) {
                                            case FILE_TYPE_NOT_ALLOWED:
                                                message = getContext().getString(
                                                    R.string.file_upload_failed_type);
                                                break;
                                            case FILE_SIZE_EXCEEDED:
                                                message = getContext().getString(
                                                    R.string.file_upload_failed_size);
                                                break;
                                            case FILE_NAME_INCORRECT:
                                                message = getContext().getString(
                                                    R.string.file_upload_failed_name);
                                                break;
                                            case UNAUTHORIZED:
                                                message = getContext().getString(
                                                    R.string.file_upload_failed_unauthorized);
                                                break;
                                            case UPLOADED_FILE_NOT_FOUND:
                                            default:
                                                message = getContext().getString(
                                                    R.string.file_upload_failed_unknown);
                                        }
                                        showToast(message, Toast.LENGTH_SHORT);
                                    }

                                }
                            });
                        return;
                    }
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
        }
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

    private void deleteFile(File file) {
        if (!file.delete()) {
            Log.w("WEBIM", "failed to deleted file " + file.getName());
        }
    }

    public void onEditMessageAction(Message message, int position) {
        inEdit = message;
        quotedMessage = null;

        editingMessagePosition = position;
        replyLayout.setVisibility(View.GONE);
        sendButton.setVisibility(View.GONE);
        editingLayout.setVisibility(View.VISIBLE);
        editButton.setVisibility(View.VISIBLE);
        editButton.setEnabled(true);
        editButton.setAlpha(1f);
        textEditingMessage.setText(message.getText());
        editTextMessage.setText(message.getText());
        editTextMessage.setSelection(message.getText().length());
    }

    public void onDeleteMessageAction(Message message) {
        session.getStream().deleteMessage(message, null);
        clearEditableMessage(message);
    }

    public void onReplyMessageAction(Message message, int position) {
        quotedMessage = message;
        if (inEdit != null) {
            editTextMessage.getText().clear();
            inEdit = null;
        }

        hideEditLayout();
        replyLayout.setVisibility(View.VISIBLE);
        replyMessagePosition = position;
        textSenderName.setText(
                (quotedMessage.getType() == VISITOR || quotedMessage.getType() == FILE_FROM_VISITOR)
                        ? this.getResources().getString(R.string.visitor_sender_name)
                        : quotedMessage.getSenderName());
        textReplyId.setText(quotedMessage.getServerSideId());
        Message.Attachment quotedMessageAttachment = quotedMessage.getAttachment();

        String replyMessage;
        if (quotedMessageAttachment != null && quotedMessageAttachment.getFileInfo().getImageInfo() != null) {
            replyMessage = getResources().getString(R.string.reply_message_with_image);
            replyThumbnail.setVisibility(View.VISIBLE);

            Message.FileInfo fileInfo = quotedMessageAttachment.getFileInfo();
            String imageUrl = fileInfo.getImageInfo().getThumbUrl();
            Glide.with(getContext())
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .into(replyThumbnail);
        } else {
            replyMessage = quotedMessage.getText();
            replyThumbnail.setVisibility(View.GONE);
        }

        textReplyMessage.setText(replyMessage);
    }

    public void onKeyBoardButtonClicked(String currentChatId, String buttonId) {
        session.getStream().sendKeyboardRequest(currentChatId, buttonId, null);
    }

    void clearEditableMessage(Message message) {
        if (editButton.getVisibility() == View.VISIBLE
                && inEdit.getServerSideId().equals(message.getServerSideId())) {
            editTextMessage.getText().clear();
            hideEditLayout();
        }
    }

    private void hideEditLayout() {
        editButton.setVisibility(View.GONE);
        editingLayout.setVisibility(View.GONE);
        sendButton.setVisibility(View.VISIBLE);
    }

    private void scrollToPosition(int position) {
        listController.recyclerView.scrollToPosition(position);
    }

    private void hideKeyboard() {
        Context context = getContext();
        if (context != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(editTextMessage.getWindowToken(), 0);
            }
        }
    }

    void updateSentMessageDialog(Message message, int adapterPosition) {
        menuController.updateSentMessageDialog(message, adapterPosition);
    }

    void updateReceivedMessageDialog(Message message, int adapterPosition) {
        menuController.updateReceivedMessageDialog(message, adapterPosition);
    }

    void setContextMenuMessageId(String menuMessageId) {
        menuController.setContextMenuMessageId(menuMessageId);
    }

    void openContextDialog(final int adapterPosition, View visibleView) {
        hideKeyboard();
        scrollToPosition(adapterPosition);
        menuController.openContextDialog(visibleView,
                                         new DynamicContextDialog.ConfigurationChangedListener() {
                                                @Override
                                                public void configurationChanged() {
                                                    scrollToPosition(adapterPosition);
                                                }
                                         });
    }

    void closeContextDialog(String messageId) {
        if (menuController.closeContextDialog(messageId)) {
            showToast(getString(R.string.message_deleted_toast), Toast.LENGTH_SHORT);
        }
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

        private ListController(final WebimChatFragment webimChatFragment,
                               final RecyclerView recyclerView,
                               final ProgressBar progressBar,
                               final MessageStream messageStream,
                               final View view) {
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
            downButton.bringToFront();

            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy < 0 && downButton.isShown()) {
                        downButton.setVisibility(View.GONE);
                    }
                }
            };

            final View syncLayout = view.findViewById(R.id.constLaySyncMessage);
            tracker.setMessagesSyncedListener(new MessageTracker.MessagesSyncedListener() {
                @Override
                public void messagesSynced() {
                    hideSyncLayout(syncLayout);
                }
            });

            scrollListener.setLoading(true);
            scrollListener.setDownButton(downButton);
            scrollListener.setAdapter(adapter);
            recyclerView.addOnScrollListener(scrollListener);
            requestMore(true);
        }

        private void hideSyncLayout(final View syncLayout) {
            long animationDuration = 500;
            int syncLayoutEndHeight = 0;
            ValueAnimator hideAnimation = ValueAnimator.ofInt(syncLayout.getMeasuredHeight(), syncLayoutEndHeight);
            hideAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    ViewGroup.LayoutParams layoutParams = syncLayout.getLayoutParams();
                    layoutParams.height = (Integer) valueAnimator.getAnimatedValue();
                    syncLayout.setLayoutParams(layoutParams);
                }
            });
            hideAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    syncLayout.setVisibility(View.GONE);
                }
            });
            hideAnimation.setDuration(animationDuration);
            hideAnimation.start();
        }

        private void requestMore() {
            requestMore(false);
        }

        private void requestMore(final boolean firstCall) {
            requestingMessages = true;
            progressBar.setVisibility(View.VISIBLE);
            if (firstCall) {
                recyclerView.setVisibility(View.GONE);
            }
            tracker.getNextMessages(MESSAGES_PER_REQUEST, new MessageTracker.GetMessagesCallback() {
                @Override
                public void receive(@NonNull final List<? extends Message> received) {
                    requestingMessages = false;
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    if (received.size() != 0) {
                        adapter.addAll(0, received);
                        adapter.notifyItemRangeInserted(adapter.getItemCount() - 1,
                                received.size());

                        if (firstCall) {
                            recyclerView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    recyclerView.smoothScrollToPosition(0);
                                    int itemCount = layoutManager.getItemCount();
                                    int lastItemVisible =
                                            layoutManager.findLastVisibleItemPosition() + 1;
                                    if (itemCount == lastItemVisible) {
                                        requestMore();
                                    }
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
                recyclerView.stopScroll();
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
                adapter.remove(pos, message.getServerSideId());
                adapter.notifyItemRemoved(adapter.getItemCount() - pos);
            }
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = adapter.lastIndexOf(from);
            if (ind != -1) {
                adapter.set(ind, to);
                adapter.notifyItemChanged(adapter.getItemCount() - ind - 1, 42);
                recyclerView.setItemAnimator(null);
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

        private void showMessage(int position) {
            recyclerView.smoothScrollToPosition(position);
        }
    }
}