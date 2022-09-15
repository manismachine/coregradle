package org.code.coregradle.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.utils.FileUtils;

import java.io.File;
import java.util.Objects;

public class IdentityStore {
    private static IdentityStore instance;
    private static JSONObject config;
    public static final String IDENTITY_KEY = "identity_key";
    public static final String IDENTITY_VALUE_KEY = "identity_value";
    public static final String DEFAULT_IDENTITY_KEY = "ingressId";
    public static final String IDENTITY_UPLOAD_HEADER_KEY = "upload_header_key";
    public static final String DEFAULT_IDENTITY_UPLOAD_HEADER_KEY = "X-INGRESS-ID";
    public static final String APP_SPECIFIC_INFO = "appSpecificInfo";
    private final File idConfig;

    @SuppressLint("HardwareIds")
    private IdentityStore(Context context) {
        idConfig = new File(context.getFilesDir(), ".id_config");
        try {
            if (idConfig.exists()) {
                config = new JSONObject(Objects.requireNonNull(FileUtils.readString(idConfig)));
            } else {
                createConfig(context);
            }
        } catch (Exception ignored) {
            createConfig(context);
        }
    }

    @SuppressLint("HardwareIds")
    private void createConfig(Context context) {
        config = new JSONObject();
        try {
            config.put(IDENTITY_KEY, DEFAULT_IDENTITY_KEY);
            config.put(IDENTITY_VALUE_KEY, Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
            config.put(IDENTITY_UPLOAD_HEADER_KEY, DEFAULT_IDENTITY_UPLOAD_HEADER_KEY);
            FileUtils.writeString(idConfig, config.toString());
        } catch (Exception ignored) {

        }
    }

    public String getIdentityKey() throws JSONException {
        return config.getString(IDENTITY_KEY);
    }

    public String getIdentityValue() throws JSONException {
        return config.getString(IDENTITY_VALUE_KEY);
    }

    public String getUploadHeaderKey() throws JSONException {
        return config.getString(IDENTITY_UPLOAD_HEADER_KEY);
    }

    public void updateConfig(String key, Object value) throws Exception {
        config.put(key, value);
        FileUtils.writeString(idConfig, config.toString());
    }

    public JSONObject getConfig() {
        return config;
    }


    public synchronized static IdentityStore getInstance(Context context) {
        if (instance == null) {
            instance = new IdentityStore(context);
        }
        return instance;
    }
}
