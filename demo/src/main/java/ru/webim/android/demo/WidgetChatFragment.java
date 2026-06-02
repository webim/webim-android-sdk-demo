package ru.webim.android.demo;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import coil.ComponentRegistry;
import coil.ImageLoader;
import coil.decode.SvgDecoder;
import coil.request.ImageRequest;
import ru.webim.android.sdk.Operator;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.WebimSession;
import ru.webim.chatview.ui.ChatView;
import ru.webim.chatview.utils.FileHelper;

public class WidgetChatFragment extends Fragment {

    private final static String SVG_EXTENSION = ".svg";
    private Webim.SessionBuilder sessionBuilder;
    private WebimSession session;
    private ChatView chatView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_widget_chat, container, false);

        chatView = rootView.findViewById(R.id.chatView);
        sessionBuilder.setErrorHandler(chatView);
        session = sessionBuilder.build();

        chatView.setSession(session);

        setPreChatActions();
        setupToolbar(rootView);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        chatView.setAutoScrollToLastMsgEnabled(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        chatView.setAutoScrollToLastMsgEnabled(false);
    }

    public void setWebimSessionBuilder(Webim.SessionBuilder session) {
        if (this.sessionBuilder != null) {
            throw new IllegalStateException("Webim session is already set");
        }
        this.sessionBuilder = session;
    }

    private void setPreChatActions() {
        Intent intent = requireActivity().getIntent();
        Bundle args = intent.getExtras();
        String type = intent.getType();
        String action = intent.getAction();

        if (args != null) {
            if (args.getBoolean(WebimChatActivity.EXTRA_SHOW_RATING_BAR_ON_STARTUP, false)) {
                chatView.addMessagesSyncedCallback(() -> chatView.getChatEditBar().showRatingDialog());
            }
        }

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent);
            } else {
                handleSendFile(intent);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            handleSendMultipleFiles(intent);
        }
    }

    private void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            chatView.addMessagesSyncedCallback(() -> session.getStream().sendMessage(sharedText));
        }
    }

    void handleSendFile(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            FileHelper fileHelper = chatView.getFileHelper();
            chatView.addMessagesSyncedCallback(() -> fileHelper.sendFileWithNewTemp(imageUri));
        }
    }

    private void handleSendMultipleFiles(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            FileHelper fileHelper = chatView.getFileHelper();
            for (Uri uri : imageUris) {
                chatView.addMessagesSyncedCallback(() -> fileHelper.sendFileWithNewTemp(uri));
            }
        }
    }

    private void setupToolbar(final View rootView) {
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(rootView.findViewById(R.id.toolbar));
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            }
        }

        final TextView typingView = rootView.findViewById(R.id.action_bar_subtitle);
        Operator operator = session.getStream().getCurrentOperator();
        setOperatorName(typingView, operator);
        setOperatorAvatar(rootView, operator);

        session.getStream().setOperatorTypingListener(isTyping -> {
            ImageView imageView = rootView.findViewById(R.id.image_typing);
            imageView.setBackgroundResource(R.drawable.typing_animation);
            AnimationDrawable animationDrawable = (AnimationDrawable) imageView.getBackground();

            if (isTyping) {
                typingView.setText(getString(R.string.operator_typing));
                typingView.setTextColor(rootView.getResources().getColor(R.color.colorTexWhenTyping));
                imageView.setVisibility(View.VISIBLE);
                animationDrawable.start();
            } else {
                setOperatorName(typingView, session.getStream().getCurrentOperator());
                typingView.setTextColor(rootView.getResources().getColor(R.color.white));
                imageView.setVisibility(View.GONE);
                animationDrawable.stop();
            }
        });
        session.getStream().setCurrentOperatorChangeListener((oldOperator, newOperator) -> {
            setOperatorAvatar(rootView, newOperator);
            setOperatorName(typingView, session.getStream().getCurrentOperator());
        });
    }

    private void setOperatorName(TextView textView, Operator operator) {
        String operatorName = operator == null
            ? getString(R.string.no_operator)
            : operator.getName();
        textView.setText(getString(R.string.operator_name, operatorName));
    }

    protected void setOperatorAvatar(View view, @Nullable Operator operator) {
        ImageView senderPhoto = view.findViewById(R.id.sender_photo);
        if (operator != null) {
            if (operator.getAvatarUrl() != null) {
                loadProfileImage(operator.getAvatarUrl(), senderPhoto);
            } else {
                if (getContext() != null) {
                    Drawable drawable = requireActivity()
                            .getResources()
                            .getDrawable(R.drawable.default_operator_avatar);
                    senderPhoto.setImageDrawable(drawable);
                }
            }
            senderPhoto.setVisibility(View.VISIBLE);
        } else {
            senderPhoto.setVisibility(View.GONE);
        }
    }

    private void loadProfileImage(String url, ImageView imageView) {
        try {
            String path = new URL(url).getPath().toLowerCase();
            if (path.endsWith(SVG_EXTENSION)) {
                ComponentRegistry componentRegistry = new ComponentRegistry.Builder()
                        .add(new SvgDecoder.Factory())
                        .build();
                ImageLoader imageLoader = new ImageLoader.Builder(requireActivity())
                        .components(componentRegistry)
                        .build();
                ImageRequest request = new ImageRequest.Builder(requireActivity())
                        .data(url)
                        .target(imageView)
                        .build();
                imageLoader.enqueue(request);
            } else {
                Glide.with(requireActivity()).load(url).into(imageView);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void onBackPressed() {
    }
}
