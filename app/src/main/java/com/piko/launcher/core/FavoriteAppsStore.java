package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persists the small set of apps the user pinned to the favourites row above the search bar. */
public final class FavoriteAppsStore {

    private static final String FILE = "piko_favorites";
    private static final String KEY_LIST = "apps";

    private final SharedPreferences sp;

    private static FavoriteAppsStore instance;

    private FavoriteAppsStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized FavoriteAppsStore get(Context context) {
        if (instance == null) instance = new FavoriteAppsStore(context.getApplicationContext());
        return instance;
    }

    public List<AppInfo> loadAll() {
        List<AppInfo> result = new ArrayList<>();
        String raw = sp.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new AppInfo(o.optString("pkg"), o.optString("act"), o.optString("label")));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public boolean isFavorite(AppInfo app) {
        for (AppInfo fav : loadAll()) if (fav.key().equals(app.key())) return true;
        return false;
    }

    public void toggle(AppInfo app) {
        if (isFavorite(app)) remove(app);
        else add(app);
    }

    public void add(AppInfo app) {
        List<AppInfo> all = loadAll();
        for (AppInfo fav : all) if (fav.key().equals(app.key())) return;
        all.add(app);
        saveAll(all);
    }

    public void remove(AppInfo app) {
        List<AppInfo> all = loadAll();
        List<AppInfo> next = new ArrayList<>();
        for (AppInfo fav : all) if (!fav.key().equals(app.key())) next.add(fav);
        saveAll(next);
    }

    private void saveAll(List<AppInfo> apps) {
        JSONArray arr = new JSONArray();
        try {
            for (AppInfo app : apps) {
                JSONObject o = new JSONObject();
                o.put("pkg", app.packageName);
                o.put("act", app.activityName);
                o.put("label", app.label);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }
}
