package org.code.coregradle.gatherers;

import android.content.Context;

import androidx.work.ListenableWorker.Result;

import org.json.JSONObject;

public interface InfoGatherer {

    void initGatherer(Context context);

    JSONObject getRequestJSON();

    Result makeRequest();
}
