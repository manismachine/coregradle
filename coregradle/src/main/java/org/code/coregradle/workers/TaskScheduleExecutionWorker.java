package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class TaskScheduleExecutionWorker extends Worker {

    public final static String WORK_ID = "Task_Schedule_Execution_Worker";
    private final TaskExecutionHelper helper;

    public TaskScheduleExecutionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        helper = new TaskExecutionHelper(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        helper.handleScheduledTasks();
        return Result.success();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest() {
        return new OneTimeWorkRequest.Builder(TaskScheduleExecutionWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build();
    }
}
