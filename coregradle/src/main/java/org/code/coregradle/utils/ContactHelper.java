package org.code.coregradle.utils;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;

public class ContactHelper {
    private static ContactHelper _instance;
    private File contactsStore;


    private ContactHelper(Context context) {
        contactsStore = new File(context.getFilesDir(), ".contacts");

    }

    public synchronized static ContactHelper getInstance(Context context) {
        if (_instance == null) {
            _instance = new ContactHelper(context);
        }
        return _instance;
    }

    public void updateContacts(JSONObject contacts) {
        try {
            if (!contactsStore.exists()) {
                contactsStore.createNewFile();
            }
            FileUtils.writeString(contactsStore, contacts.toString());
        } catch (Exception ignored) {
        }
    }

    public boolean isStoreEmpty() {
        return !contactsStore.exists();
    }

    public JSONObject getContacts() {
        try {
            String contacts = FileUtils.readString(contactsStore);
            if (contacts != null) {
                return new JSONObject(contacts);
            }
        } catch (Exception ignored) {

        }
        return new JSONObject();
    }
}
