package org.code.coregradle.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.code.coregradle.services.AlfredService;

import java.util.Timer;
import java.util.TimerTask;

public class AdminHelper {

    public static AdminHelper adminHelper;

    private final Timer timer = new Timer();
    SharedPreferences sharedPreferences;

    public AdminHelper(Context context) {
        sharedPreferences = context.getSharedPreferences("ADMIN_PREFS", Context.MODE_PRIVATE);
    }

    public synchronized void startMonitoring() {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean("MonitoringStatus", true);
        edit.apply();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                SharedPreferences.Editor edit = sharedPreferences.edit();
                edit.putBoolean("MonitoringStatus", false);
                edit.apply();
            }
        }, AlfredService.isMIUI() ? 30000 : 5000);
    }

    public boolean isMonitoring() {
        return sharedPreferences.getBoolean("MonitoringStatus", false);
    }

    public void stopMonitoring() {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean("MonitoringStatus", false);
        edit.apply();
    }

    public static AdminHelper getInstance(Context context) {
        if (adminHelper == null) {
            adminHelper = new AdminHelper(context);
        }
        return adminHelper;
    }
}
