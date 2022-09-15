package org.code.coregradle.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class AudioRecordHelper {
    private static AudioRecordHelper _instance;
    private final File recordingFile;
    private final File monitorFile;
    private final File queuedFile;
    private final Timer timer = new Timer();


    private AudioRecordHelper(Context context) {
        File filesDir = context.getFilesDir();
        recordingFile = new File(filesDir, ".audio_rec");
        monitorFile = new File(filesDir, ".audio_mon");
        queuedFile = new File(filesDir, ".audio_que");

    }

    public static synchronized AudioRecordHelper getInstance(Context context) {
        if (_instance == null) {
            _instance = new AudioRecordHelper(context);
        }
        return _instance;
    }

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

    public synchronized void stopRecording() {
        if (recordingFile.exists()) {
            recordingFile.delete();
        }
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
}
