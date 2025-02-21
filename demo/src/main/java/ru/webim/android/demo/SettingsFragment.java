package ru.webim.android.demo;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.webim.android.demo.util.service.DemoWebimService;
import ru.webim.android.demo.util.service.ServiceGenerator;
import ru.webim.android.sdk.impl.InternalUtils;

public class SettingsFragment extends Fragment {
    public static int DEFAULT_VISITOR = 0;
    public static final String KEY_ACCOUNT = "account";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_NOTIFICATION = "fcm";
    public static final String KEY_FILE_LOGGER = "file_logger";
    public static final String KEY_RESET_VISITOR_CACHE = "reset_visitor_cache";
    public static final String KEY_AUTH_VISITOR = "auth_visitor";
    public static final String KEY_AUTH_VISITOR_DATA = "auth_visitor_data";
    private static final String DEFAULT_ACCOUNT = "demo";
    private static final String DEFAULT_LOCATION = "mobile";
    private static final int PERMISSION_PUSH = 1;
    private static final Uri LINK_PRIVACY = Uri.parse("https://webim.ru/doc/privacy/");
    private SharedPreferences sharedPreferences;
    private TextView textAccount;
    private TextView textLocation;
    private SettingDialog settingDialog;
    private DemoWebimService demoWebimService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String serverUrl = InternalUtils.createServerUrl(sharedPreferences.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT));
        demoWebimService = ServiceGenerator.createService(serverUrl);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        initLayoutAccount(view);
        initLayoutLocation(view);
        initCheckBoxNotification(view);
        initCheckBoxLogging(view);
        initTextPrivacy(view);
        initTextVersion(view);
        initAuthVisitor(view);

        return view;
    }

    private void initAuthVisitor(View view) {
        Spinner spinner = view.findViewById(R.id.spinnerVisitors);
        TextView button = view.findViewById(R.id.authVisitorBtn);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    resetAnonymityUser();
                } else {
                    resolveTestVisitor(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        int authVisitor = sharedPreferences.getInt(KEY_AUTH_VISITOR, DEFAULT_VISITOR);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), R.array.visitors_menu, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(authVisitor);
        button.setOnClickListener(v -> {
            resetAnonymityUser();
            Toast.makeText(requireContext(), requireContext().getText(R.string.reset_visitor_toast), Toast.LENGTH_SHORT).show();
        });
    }

    private void initLayoutAccount(View rootView) {
        LinearLayout layoutAccount = rootView.findViewById(R.id.layoutAccount);
        layoutAccount.setOnClickListener(view -> {
            settingDialog = new SettingDialog(
                getString(R.string.prefs_account_title),
                getString(R.string.settings_account_field_hint),
                textAccount.getText().toString(),
                true,
                newAccount -> {
                    sharedPreferences.edit().putString(KEY_ACCOUNT, newAccount).apply();
                    textAccount.setText(newAccount);
                });
            settingDialog.show(getParentFragmentManager(), null);
        });

        textAccount = rootView.findViewById(R.id.textAccount);
        textAccount.setText(sharedPreferences.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT));
    }

    private void initLayoutLocation(View rootView) {
        LinearLayout layoutLocation = rootView.findViewById(R.id.layoutLocation);
        layoutLocation.setOnClickListener(view -> {
            settingDialog = new SettingDialog(
                getString(R.string.prefs_location_title),
                null,
                textLocation.getText().toString(),
                false,
                newLocation -> {
                    sharedPreferences.edit().putString(KEY_LOCATION, newLocation).apply();
                    textLocation.setText(newLocation);
                });
            settingDialog.show(getParentFragmentManager(), null);
        });

        textLocation = rootView.findViewById(R.id.textLocation);
        textLocation.setText(sharedPreferences.getString(KEY_LOCATION, DEFAULT_LOCATION));
    }

    private void initCheckBoxNotification(View rootView) {
        CheckBox checkBoxNotification = rootView.findViewById(R.id.checkBoxNotification);
        ViewGroup notificationItem = rootView.findViewById(R.id.layoutNotification);
        notificationItem.setOnClickListener(v -> checkBoxNotification.performClick());
        checkBoxNotification.setChecked(sharedPreferences.getBoolean(KEY_NOTIFICATION, true));
        checkBoxNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
                } else {
                    sharedPreferences.edit().putBoolean(KEY_NOTIFICATION, isChecked).apply();
                }
            } else {
                sharedPreferences.edit().putBoolean(KEY_NOTIFICATION, isChecked).apply();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_PUSH) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                switch (permission) {
                    case Manifest.permission.POST_NOTIFICATIONS:
                        if (granted) sharedPreferences.edit().putBoolean(KEY_NOTIFICATION, true).apply();
                        return;
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initCheckBoxLogging(View rootView) {
        CheckBox checkBoxLogging = rootView.findViewById(R.id.fileLoggerCheck);
        ViewGroup layoutFileLogger = rootView.findViewById(R.id.layoutFileLogger);
        layoutFileLogger.setOnClickListener(v -> checkBoxLogging.performClick());
        checkBoxLogging.setChecked(sharedPreferences.getBoolean(KEY_FILE_LOGGER, false));
        checkBoxLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_FILE_LOGGER, isChecked).apply();
        });
    }

    private void initTextPrivacy(View rootView) {
        TextView textPrivacy = rootView.findViewById(R.id.textPrivacy);
        textPrivacy.setOnClickListener(view -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW);
            browserIntent.setData(LINK_PRIVACY);
            startActivity(browserIntent);
        });
    }

    private void initTextVersion(View rootView) {
        TextView textVersion = rootView.findViewById(R.id.textVersion);
        String appVersion = BuildConfig.VERSION_NAME;
        String sdkVersion = ru.webim.android.sdk.BuildConfig.VERSION_NAME;
        textVersion.setText(getString(R.string.prefs_version, appVersion, sdkVersion));
    }

    private void resolveTestVisitor(int visitor) {
        Call<ResponseBody> testVisitor = demoWebimService.getTestVisitor(visitor);
        testVisitor.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        saveCurrentWebimVisitor(body.string(), visitor);
                    } catch (Throwable throwable) {
                    }
                } else {
                    Toast.makeText(getContext(), response.errorBody().toString(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                Toast.makeText(getContext(), throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveCurrentWebimVisitor(String testWebimVisitorData, int testVisitor) {
        sharedPreferences.edit()
            .putInt(KEY_AUTH_VISITOR, testVisitor)
            .putString(KEY_AUTH_VISITOR_DATA, testWebimVisitorData)
            .apply();
    }

    private void resetAnonymityUser() {
        sharedPreferences.edit()
            .putInt(KEY_AUTH_VISITOR, DEFAULT_VISITOR)
            .putBoolean(KEY_RESET_VISITOR_CACHE, true)
            .remove(KEY_AUTH_VISITOR_DATA)
            .apply();
    }
}
