package org.code.coregradle.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;

public class NetworkUtils {
    private static final String TAG = "Coregradle::NetworkUtils";

    private static JSONObject toConnection(String[] header, String line) throws Exception {
        String[] entry = line.split("\\s{2,}");
        JSONObject connection = new JSONObject();
        for (int i = 0; i < Math.min(header.length, entry.length); i++) {
            connection.put(header[i], entry[i]);
        }
        return connection;
    }

    public static JSONObject getConnectedDevices() {
        JSONObject result = new JSONObject();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line = "";
            String[] header = bufferedReader.readLine().split("\\s{2,}");
            JSONArray connections = new JSONArray();
            while ((line = bufferedReader.readLine()) != null) {
                connections.put(toConnection(header, line));
            }
            result.put("connections", connections);
            return result;
        } catch (Exception e) {
            Log.d(TAG, "Error: ", e);
        }
        return result;
    }
}

