package com.piko.launcher;

import android.app.Application;

import com.piko.launcher.core.AppIndexer;

/** Kicks off app indexing the moment the process starts, before any UI exists. */
public final class PikoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppIndexer.get(this).loadAsync();
    }
}
