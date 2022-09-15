package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class TaskSchedulingWorker extends Worker {
    public final static String WORK_IDENTIFIER = "Task_Scheduling_Worker";

    public TaskSchedulingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniqueWork(TaskScheduleExecutionWorker.WORK_ID,
                        ExistingWorkPolicy.REPLACE,
                        TaskScheduleExecutionWorker.getOneTimeWorkRequest());
        return Result.success();
    }

    public static PeriodicWorkRequest getPeriodicWorkRequest(int periodicity) {
        return new PeriodicWorkRequest.Builder(TaskScheduleExecutionWorker.class, periodicity, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .build();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        return new OneTimeWorkRequest.Builder(TaskSchedulingWorker.class)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Long.MAX_VALUE, TimeUnit.MINUTES)
                .build();
    }

}
