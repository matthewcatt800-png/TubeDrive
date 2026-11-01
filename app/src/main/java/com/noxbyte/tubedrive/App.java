package com.noxbyte.tubedrive;

import android.app.Application;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "TubeDrive";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            com.noxbyte.tubedrive.yt.YouTube.init();
        } catch (Throwable t) {
            Log.e(TAG, "YouTube init failed", t);
        }
    }
}
