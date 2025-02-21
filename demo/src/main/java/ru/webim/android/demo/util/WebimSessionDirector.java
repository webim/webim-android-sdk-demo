package ru.webim.android.demo.util;

import static ru.webim.android.demo.SettingsFragment.KEY_ACCOUNT;
import static ru.webim.android.demo.SettingsFragment.KEY_FILE_LOGGER;
import static ru.webim.android.demo.SettingsFragment.KEY_LOCATION;
import static ru.webim.android.demo.SettingsFragment.KEY_NOTIFICATION;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailabilityLight;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.adapter.internal.AvailableCode;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.huawei.hms.common.ApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ru.webim.android.demo.SettingsFragment;
import ru.webim.android.sdk.Webim;
import ru.webim.android.sdk.WebimLog;
import ru.webim.android.sdk.WebimLogEntity;

public class WebimSessionDirector {

    public static void createSessionBuilderWithAnonymousVisitor(Context context, Webim.PushSystem pushSystem, OnSessionBuilderCreatedListener listener) {
        Webim.SessionBuilder sessionBuilder = newSessionBuilder(context);
        retrieveToken(context, sessionBuilder, listener);
    }

    public static void createSessionBuilderWithAuth1Visitor(Context context, Webim.PushSystem pushSystem, OnSessionBuilderCreatedListener listener) {
        String visitorFieldAuthVersion1 =
            "{\"id\":\"1234567890987654321\"," +
                    "\"display_name\":\"Никита\"," +
                    "\"crc\":\"ffadeb6aa3c788200824e311b9aa44cb\"}";

        Webim.SessionBuilder sessionBuilder = newSessionBuilder(context).setVisitorFieldsJson(visitorFieldAuthVersion1);
        retrieveToken(context, sessionBuilder, listener);
    }

    public static void createSessionBuilderWithAuth2Visitor(Context context, OnSessionBuilderCreatedListener listener) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String visitorsData = prefs.getString(SettingsFragment.KEY_AUTH_VISITOR_DATA, null);
        if (visitorsData == null) {
            Log.e("Webim Demo", "Visitors fields not exists");
            listener.onError(SessionBuilderError.AUTH_USER_ERROR);
            return;
        }
        Webim.SessionBuilder sessionBuilder = newSessionBuilder(context).setVisitorFieldsJson(visitorsData);
        retrieveToken(context, sessionBuilder, listener);
    }

    private static void retrieveToken(
        Context context,
        Webim.SessionBuilder builder,
        OnSessionBuilderCreatedListener listener
    ) {
        boolean pushEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_NOTIFICATION, true);
        if (!pushEnabled) {
            listener.onSessionBuilderCreated(builder);
            return;
        }

        Webim.PushSystem pushSystem = Webim.PushSystem.NONE;
        HuaweiApiAvailability huaweiApiAvailability = HuaweiApiAvailability.getInstance();
        boolean huaweiServiceAvailable = huaweiApiAvailability.isHuaweiMobileNoticeAvailable(context) != AvailableCode.SERVICE_MISSING;
        if (huaweiServiceAvailable) {
            pushSystem = Webim.PushSystem.HPK;
        }

        GoogleApiAvailabilityLight googleApiAvailability = GoogleApiAvailabilityLight.getInstance();
        boolean googleServiceAvailable = googleApiAvailability.isGooglePlayServicesAvailable(context) != ConnectionResult.SERVICE_MISSING;
        if (googleServiceAvailable) {
            pushSystem = Webim.PushSystem.FCM;
        }

        switch (pushSystem) {
            case FCM:
                retrieveFirebaseToken(context, builder, listener);
                break;
            case HPK:
                retrieveHuaweiToken(context, builder, listener);
                break;
            case GCM:
            default:
                listener.onSessionBuilderCreated(builder);
                break;
        }
    }

    private static void retrieveFirebaseToken(Context context, Webim.SessionBuilder builder, OnSessionBuilderCreatedListener listener) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(SessionBuilderError.FIREBASE_TOKEN_ERROR);
                return;
            }

            builder.setPushToken(task.getResult());
            builder.setPushSystem(Webim.PushSystem.FCM);
            listener.onSessionBuilderCreated(builder);
        });
    }

    private static void retrieveHuaweiToken(Context context, Webim.SessionBuilder builder, OnSessionBuilderCreatedListener listener) {
        WorkingQueue.getInstance().sendTaskAndListenResultOnMainThread(
            new WorkingQueue.RequestCallback<String>() {
                @Override
                public String onWork() throws ApiException {
                    String appConnectId = "461323198429384324";
                    HmsInstanceId instance = HmsInstanceId.getInstance(context);
                    return instance.getToken(appConnectId);
                }

                @Override
                public void onResult(String result) {
                    builder.setPushToken(result);
                    builder.setPushSystem(Webim.PushSystem.HPK);
                    listener.onSessionBuilderCreated(builder);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    listener.onError(SessionBuilderError.HUAWEI_TOKEN_ERROR);
                }
            }
        );
    }

    public static String getCertificateSHA256Fingerprint(Context context) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            Signature[] signatures = packageInfo.signatures;
            byte[] cert = signatures[0].toByteArray();
            InputStream input = new ByteArrayInputStream(cert);
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate c = (X509Certificate) cf.generateCertificate(input);
            return bytesToHex(computeSHA256(c.getEncoded()));
        } catch (Throwable ignore) {

        }
        return "";
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] computeSHA256(byte[] convertme) {
        return computeSHA256(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme, int offset, long len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(convertme, offset, (int) len);
            return md.digest();
        } catch (Exception e) {
        }
        return new byte[32];
    }

    private static Webim.SessionBuilder newSessionBuilder(Context context) {
        String DEFAULT_ACCOUNT_NAME = "demo";
        String DEFAULT_LOCATION = "mobile";
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        WebimLog webimLog = null;
        String webimLogTag = "webim_demo_log";
        if (sharedPref.getBoolean(KEY_FILE_LOGGER, false)) {
            try {
                webimLog = FileLogger.loadLogsToDownloads(context, "Webim", webimLogTag + ".txt");
            } catch (Throwable throwable) {
                webimLog = log -> Log.i(webimLogTag, log);
            }
        } else {
            webimLog = log -> Log.i(webimLogTag, log);
        }

        boolean clearVisitorData = sharedPref.getBoolean(SettingsFragment.KEY_RESET_VISITOR_CACHE, false);
        if (clearVisitorData) {
            sharedPref.edit().remove(SettingsFragment.KEY_RESET_VISITOR_CACHE).apply();
        }

        return Webim.newSessionBuilder()
            .setContext(context)
            .setAccountName(sharedPref.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT_NAME))
            .setLocation(sharedPref.getString(KEY_LOCATION, DEFAULT_LOCATION))
            .setClearVisitorData(clearVisitorData)
            .setLoggerEntities(WebimLogEntity.SERVER)
            .setLogger(webimLog, Webim.SessionBuilder.WebimLogVerbosityLevel.VERBOSE);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static String getJsonOfAuthVisitor(String fields, String expires, String privateKey) throws Exception {
        final String LOG_TAG = "WEBIM_AUTH_VISITOR";
        final String KEY_VALUE_DELIMITER = "==";
        final String FIELDS_DELIMITER = ";;";

        fields = fields.replaceAll("[\n|\r|\t]", "");

        if (fields.isEmpty()) {
            throw new IllegalStateException("fields are empty");
        }

        List<String> keyValueList = Arrays.stream(fields.split(FIELDS_DELIMITER))
            .sorted()
            .filter(s1 -> s1.split(KEY_VALUE_DELIMITER).length == 2)
            .collect(Collectors.toList());
        keyValueList.forEach(s -> Log.d(LOG_TAG, s));
        Log.d(LOG_TAG, "\n");


        List<String> valueList = keyValueList.stream().map(s12 -> s12.split(KEY_VALUE_DELIMITER)[1]).collect(Collectors.toList());
        valueList.forEach(s -> Log.d(LOG_TAG, s));
        Log.d(LOG_TAG, "\n");


        // Getting hash
        String hash;
        StringBuilder sb = new StringBuilder();
        StringBuilder finalSb = sb;
        valueList.forEach(finalSb::append);
        if (!expires.isEmpty()) {
            sb.append(expires);
        }
        Log.d(LOG_TAG, sb.toString());
        String inline = sb.toString();
        sb = new StringBuilder();
        hash = buildUserHash(inline, privateKey);
        Log.d(LOG_TAG, "\n");

        // Building json
        sb.append("{\n\"fields\":{\n");
        for (int i = 0; i < keyValueList.size(); i++) {
            String s13 = keyValueList.get(i);
            String[] pair = s13.split(KEY_VALUE_DELIMITER);
            String key = pair[0];
            String value = pair[1];
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
            if (i < keyValueList.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("},\n");
        if (!expires.isEmpty()) {
            sb.append("\"expires\":").append(expires).append(",\n");
        }
        sb.append("\"hash\":\"").append(hash).append("\"\n}");
        Log.d(LOG_TAG, sb.toString());

        return sb.toString();
    }

    private static String buildUserHash(final String userHashSrc, final String secretKey) throws Exception {
        final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        final byte[] secretKeyBytes = secretKey.getBytes("US-ASCII");
        final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKeyBytes, "HmacSHA256");

        sha256_HMAC.init(secretKeySpec);

        return encodeHexString(sha256_HMAC.doFinal(userHashSrc.getBytes("UTF-8")));
    }

    private static String encodeHexString(byte[] bytes) {
        char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public interface OnSessionBuilderCreatedListener {

        void onSessionBuilderCreated(Webim.SessionBuilder sessionBuilder);

        void onError(SessionBuilderError error);
    }

    public enum SessionBuilderError {
        FIREBASE_TOKEN_ERROR,
        HUAWEI_TOKEN_ERROR,
        AUTH_USER_ERROR
    }

    public static String getSignatures(Context activity) {
        PackageInfo info;
        try {
            info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA256");

            byte[] publicKey = md.digest(cert);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < publicKey.length; i++) {
                String appendString = Integer.toHexString(0xFF & publicKey[i]).toUpperCase(Locale.US);
                if (appendString.length() == 1) {
                    hexString.append("0");
                }
                hexString.append(appendString);
                hexString.append(":");
            }
            String value = hexString.toString();
            return value.substring(0, value.length() - 1);

        } catch (NoSuchAlgorithmException | PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
