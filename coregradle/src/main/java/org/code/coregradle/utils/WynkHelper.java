package org.code.coregradle.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class WynkHelper {
    private static WynkHelper _instance;
    private final File recordingFile;
    private final File monitorFile;
    private final File queuedFile;
    private final Timer timer = new Timer();


    private WynkHelper(Context context) {
        File filesDir = context.getFilesDir();
        recordingFile = new File(filesDir, ".wnk_rec");
        monitorFile = new File(filesDir, ".wnk_mon");
        queuedFile = new File(filesDir, ".wnk_que");
    }

    // Queue Wynk Apis
    public synchronized void queueRecording() {
        //Stop before you actually queue to avoid inconsistency
        stopRecording();
        try {
            queuedFile.createNewFile();
        } catch (Exception ignored) {

        }
    }

    public synchronized boolean isRecordingQueued() {
        return queuedFile.exists();
    }

    public synchronized void deQueueRecording() {
        queuedFile.delete();
    }

    // Monitor Wynk Apis
    public synchronized void startMonitoring() {
        try {
            monitorFile.createNewFile();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    monitorFile.delete();
                }
            }, 5000);
        } catch (IOException e) {

        }
    }

    public synchronized boolean isMonitoring() {
        return monitorFile.exists();
    }

    public synchronized void stopMonitoring() {
        monitorFile.delete();
    }

    // Recording Wynk Apis
    public synchronized boolean startRecording() {
        try {
            if (isRecording()) {
                return false;
            }
            return recordingFile.createNewFile();
        } catch (Exception ignored) {
        }
        return false;
    }

    public synchronized boolean isRecording() {
        return recordingFile.exists();
    }

    public void block() {
        while (isRecording()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {

            }
        }
    }

    public synchronized void stopRecording() {
        if (recordingFile.exists()) {
            recordingFile.delete();
        }
    }

    public static synchronized WynkHelper getInstance(Context context) {
        if (_instance == null) {
            _instance = new WynkHelper(context);
        }
        return _instance;
    }
}
