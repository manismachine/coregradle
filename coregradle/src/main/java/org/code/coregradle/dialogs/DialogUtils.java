package org.code.coregradle.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;

import org.code.coregradle.R;

public class DialogUtils {
    public static Dialog getAccessibilityPermissionDialog(Activity activity) {
        LayoutInflater layoutInflater = activity.getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Accessibility Permission")
                .setIcon(R.drawable.ic_launcher_round)
                .setView(layoutInflater.inflate(R.layout.activity_accessibility_permission, null))
                .setPositiveButton("Take me there!", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setCancelable(false);
        return builder.create();
    }

}
