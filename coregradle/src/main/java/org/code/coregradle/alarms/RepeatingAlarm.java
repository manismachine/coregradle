package org.code.coregradle.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.code.coregradle.activities.CrossActivity;

import java.util.Calendar;

public class RepeatingAlarm {
    public static void schedule(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, CoregradleReceiver.class);
            Intent activityIntent = new Intent(context, CrossActivity.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent showIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, 90);
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), showIntent);
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        }
    }

    public static void schedule(Context context, int delay) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, CoregradleReceiver.class);
            Intent activityIntent = new Intent(context, CrossActivity.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent showIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, delay);
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), showIntent);
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        }
    }
}
