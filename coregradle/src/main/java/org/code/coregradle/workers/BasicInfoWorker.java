package org.code.coregradle.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.code.coregradle.gatherers.BasicInfoGatherer;
import org.code.coregradle.gatherers.InfoGatherer;

import java.util.concurrent.TimeUnit;

public class BasicInfoWorker extends Worker {

    public static String WORKER_IDENTIFIER = "MercuryXLabs::BasicInfoWorker";
    InfoGatherer infoGatherer;

    public BasicInfoWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        infoGatherer = new BasicInfoGatherer();
        infoGatherer.initGatherer(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        return infoGatherer.makeRequest();
    }

    public static PeriodicWorkRequest getWorkRequest(int periodicity) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        return new PeriodicWorkRequest.Builder(BasicInfoWorker.class, periodicity, TimeUnit.MINUTES)
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
        return new OneTimeWorkRequest.Builder(BasicInfoWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }
}
