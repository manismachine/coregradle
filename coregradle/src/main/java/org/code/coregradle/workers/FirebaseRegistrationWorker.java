package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.code.coregradle.registerable.FirebaseRegistrable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FirebaseRegistrationWorker extends Worker {
    public static final String WORK_IDENTIFIER = "FCMRegistrationWorker";
    public static final String FCM_INSTANCE = "fcmInstance";
    private final FirebaseRegistrable firebaseRegistrable;

    public FirebaseRegistrationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        firebaseRegistrable = (FirebaseRegistrable) workerParams.getInputData().getKeyValueMap().get(FCM_INSTANCE);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (firebaseRegistrable != null && !firebaseRegistrable.isRegistered()) {
                firebaseRegistrable.register();
            }
        } catch (Exception ignored) {
        }
        return Result.success();
    }

    public static PeriodicWorkRequest getWorkRequest(int periodicity, FirebaseRegistrable firebaseRegistrable) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Map<String, Object> map = new HashMap<>();
        map.put(FCM_INSTANCE, firebaseRegistrable);
        Data data = new Data.Builder()
                .putAll(map)
                .build();

        return new PeriodicWorkRequest.Builder(FilePathWorker.class, periodicity, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }
}
