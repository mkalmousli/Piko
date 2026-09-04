package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-app overrides the user set from the "Edit app" screen: a rename, extra
 * search-only tags, and/or a custom icon file path. Keyed by
 * {@link AppInfo#key()} so overrides survive package updates.
 */
public final class AppCustomizationStore {

    private static final String FILE = "piko_app_customization";

    private final SharedPreferences sp;

    private static AppCustomizationStore instance;

    private AppCustomizationStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized AppCustomizationStore get(Context context) {
        if (instance == null) instance = new AppCustomizationStore(context.getApplicationContext());
        return instance;
    }

    public static final class Entry {
        public String label;
        public String tags = "";
        public String iconPath;
    }

    public Entry get(String key) {
        String raw = sp.getString(key, null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            Entry e = new Entry();
            e.label = o.optString("label", null);
            e.tags = o.optString("tags", "");
            e.iconPath = o.optString("iconPath", null);
            return e;
        } catch (JSONException e) {
            return null;
        }
    }

    public void save(String key, String label, String tags, String iconPath) {
        JSONObject o = new JSONObject();
        try {
            if (label != null) o.put("label", label);
            o.put("tags", tags == null ? "" : tags);
            if (iconPath != null) o.put("iconPath", iconPath);
        } catch (JSONException ignored) {
        }
        sp.edit().putString(key, o.toString()).apply();
    }

    public void clear(String key) {
        sp.edit().remove(key).apply();
    }

    /** Applies any stored override to a freshly-indexed app, leaving unmodified apps untouched. */
    public AppInfo applyTo(AppInfo raw) {
        Entry e = get(raw.key());
        if (e == null) return raw;
        String label = e.label != null && !e.label.trim().isEmpty() ? e.label : raw.label;
        return new AppInfo(raw.packageName, raw.activityName, label, e.tags, e.iconPath);
    }

    public List<AppInfo> applyAll(List<AppInfo> rawApps) {
        List<AppInfo> result = new ArrayList<>(rawApps.size());
        for (AppInfo app : rawApps) result.add(applyTo(app));
        return result;
    }
}
