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

import org.code.coregradle.gatherers.InfoGatherer;
import org.code.coregradle.gatherers.UploadGatherer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class UploadWorker extends Worker {
    public static String WORKER_IDENTIFIER = "MercuryXLabs::UploadWorker";

    private final InfoGatherer infoGatherer;

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        infoGatherer = new UploadGatherer();
        infoGatherer.initGatherer(context);
    }

    public static byte[] readAllBytes(InputStream inputStream) {
        final int bufLen = 4 * 0x400; // 4KB
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                outputStream.write(buf, 0, readLen);

            return outputStream.toByteArray();
        } catch (Exception ignored) {
            return new byte[0];
        }

    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
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
        return new PeriodicWorkRequest.Builder(UploadWorker.class, periodicity, TimeUnit.MINUTES)
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
        return new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }

    public static OneTimeWorkRequest getOneTimeWorkRequest(int initialDelay) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        return new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.SECONDS)
                .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .build();
    }
}
