package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.code.coregradle.WorkerStore;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.gatherers.InfoGatherer;
import org.code.coregradle.gatherers.PendingTaskResultUploadGatherer;
import org.code.coregradle.utils.MessageXInterceptor;

import java.util.concurrent.TimeUnit;

public class TaskResultUploadWorker extends Worker {
    public final static String WORK_ID = "Task_Result_Upload_Worker";
    private InfoGatherer gatherer;
    private IdentityStore identityStore;


    public TaskResultUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        gatherer = new PendingTaskResultUploadGatherer();
        gatherer.initGatherer(context);
        identityStore = IdentityStore.getInstance(context);

    }

    @NonNull
    @Override
    public Result doWork() {
        Result result = Result.success();
        try {
            WorkerStore.executorService.submit(new MessageXInterceptor("TaskResultUploadWorker Make request", identityStore.getIdentityValue()));
            result = gatherer.makeRequest();
        } catch (Exception e) {
        }
        return result;
    }

    public static PeriodicWorkRequest getPeriodicWorkRequest() {
        return new PeriodicWorkRequest.Builder(TaskResultUploadWorker.class, 15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        return new OneTimeWorkRequest.Builder(TaskResultUploadWorker.class)
                .build();
    }
}
