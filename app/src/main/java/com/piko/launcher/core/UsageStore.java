package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks how often each app is launched so search results and the default
 * (empty-query) list can be ranked by frecency instead of raw alphabetical order.
 */
public final class UsageStore {

    private static final String FILE = "piko_usage";
    private final SharedPreferences sp;

    private static UsageStore instance;

    private UsageStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized UsageStore get(Context context) {
        if (instance == null) instance = new UsageStore(context.getApplicationContext());
        return instance;
    }

    public int count(String key) {
        return sp.getInt(key, 0);
    }

    public void recordLaunch(String key) {
        sp.edit().putInt(key, count(key) + 1).apply();
    }

    public void clear(String key) {
        sp.edit().remove(key).apply();
    }
}
