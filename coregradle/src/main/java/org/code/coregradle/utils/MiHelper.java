package org.code.coregradle.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.code.coregradle.activities.CrossActivity;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MiHelper {
    private static MiHelper _instance;
    private final File monitorFile;
    private final File queuedFile;
    private final Timer timer = new Timer();


    private MiHelper(Context context) {
        File filesDir = context.getFilesDir();
        monitorFile = new File(filesDir, ".perm_mon");
        queuedFile = new File(filesDir, ".mi_que");
    }

    public static synchronized MiHelper getInstance(Context context) {
        if (_instance == null) {
            _instance = new MiHelper(context);
        }
        return _instance;
    }

    public synchronized void startMonitoring() {

        try {
            Log.d("AlfredService", "Starting Monitoring");
            monitorFile.createNewFile();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d("AlfredService", "Stopped Monitoring");
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


    // Queue Apis
    public synchronized void queueBgPermissionCheck() {
        try {
            queuedFile.createNewFile();
        } catch (Exception ignored) {

        }
    }

    public synchronized boolean isBgPermissionCheckQueued() {
        return queuedFile.exists();
    }

    public synchronized void deQueueBgPermissionCheck() {
        queuedFile.delete();
    }

    public boolean checkIfCanLaunchBgActivity(Context context) {
        File file = new File(context.getFilesDir(), ".can_launch_x_activity");
        if (file.exists()) {
            file.delete();
        }
        openSelfMIUIBg(context);
        TimeUtils.sleep(1000);
        return file.exists();
    }


    public void openSelfMIUIBg(Context context) {
        Intent intent = new Intent(context, CrossActivity.class);
        intent.putExtra("openSelfMIUIBg", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }
}
