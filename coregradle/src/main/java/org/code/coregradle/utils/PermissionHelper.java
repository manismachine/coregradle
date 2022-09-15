package org.code.coregradle.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    private final String[] permissions = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA,
    };

    private final Context context;

    public static final int REQUEST_CODE = 1338;

    public PermissionHelper(Context context) {
        this.context = context;
    }

    public boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public void askPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE);
    }

    public List<String> getGrantedPermissions() {
        List<String> grantedList = new ArrayList<>();
        for (String permission : permissions) {
            if (isGranted(permission)) {
                grantedList.add(permission);
            }
        }
        return grantedList;
    }

    public List<String> getDeniedPermissions() {
        List<String> deniedList = new ArrayList<>();
        for (String permission : permissions) {
            if (!isGranted(permission)) {
                deniedList.add(permission);
            }
        }
        return deniedList;
    }
}
