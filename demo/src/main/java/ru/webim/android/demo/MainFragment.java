package ru.webim.android.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import ru.webim.android.sdk.WebimSession;

public class MainFragment extends Fragment {

    private static final String TAG = "MainFragment";
    private WebimSession session;
    private View newMessagesLayout;
    private TextView newMessagesTextView;
    private TextView subTitle;
    private TextView description;
    private Button onboardingButton;
    private Button startChatButton;
    private Button settingsButton;
    private MainFragmentDelegate delegate;

    public interface MainFragmentDelegate {
        void onOpenChat();

        void onOpenSettings();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        delegate = (MainFragmentDelegate) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkNotificationPermission();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initOnboardingButton(view);
        initNewChatButton(view);
        initViewForBadge(view);
        initSettingButton(view);

        updateButtonVisibility();
    }

    private void initViews(View rootView) {
        subTitle = rootView.findViewById(R.id.welcomeSubtitleTextView);
        description = rootView.findViewById(R.id.welcomeDescriptionTextView);
    }

    private void initOnboardingButton(View rootView) {
        onboardingButton = rootView.findViewById(R.id.buttonOnboarding);
        onboardingButton.setOnClickListener(view -> delegate.onOpenSettings());
    }

    private void initNewChatButton(View rootView) {
        startChatButton = rootView.findViewById(R.id.buttonStartChat);
        startChatButton.setOnClickListener(view -> delegate.onOpenChat());
    }

    private void initViewForBadge(View rootView) {
        newMessagesLayout = rootView.findViewById(R.id.linearLayoutNewMessages);
        newMessagesTextView = rootView.findViewById(R.id.newMessagesTextView);
    }

    private void initSettingButton(View rootView) {
        settingsButton = rootView.findViewById(R.id.buttonSettings);
        settingsButton.setOnClickListener(view -> delegate.onOpenSettings());
    }


    @Override
    public void onStart() {
        super.onStart();
        if (session != null) {
            session.resume();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (session != null) {
            session.resume();
        }
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        boolean isOnboarding = SettingsFragment.needOnboarding(requireContext());

        subTitle.setText(isOnboarding ? R.string.welcome_subtitle : R.string.welcome_subtitle_start_chat);
        description.setVisibility(isOnboarding ? View.VISIBLE : View.GONE);

        if (onboardingButton != null) {
            onboardingButton.setVisibility(isOnboarding ? View.VISIBLE : View.GONE);
        }
        if (startChatButton != null) {
            startChatButton.setVisibility(isOnboarding ? View.GONE : View.VISIBLE);

            if (!isOnboarding) {
                initCounter();
            }
        }
        if (settingsButton != null) {
            settingsButton.setVisibility(isOnboarding ? View.GONE : View.VISIBLE);
        }
    }

    private void initCounter() {
        if (session == null) {
            return;
        }
        newMessagesLayout.setVisibility(View.GONE);
        session.getStream().setUnreadByVisitorMessageCountChangeListener(newMessageCount -> {
            if (newMessageCount > 0) {
                newMessagesTextView.setText(getString(R.string.welcome_new_messages, newMessageCount));
                newMessagesLayout.setVisibility(View.VISIBLE);
            } else {
                newMessagesLayout.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (session != null) {
            session.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.destroy();
        }
    }

    public void setWebimSession(WebimSession webimSession) {
        session = webimSession;
    }
}
