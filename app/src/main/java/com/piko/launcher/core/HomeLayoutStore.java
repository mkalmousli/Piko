package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Persists the icon/widget grid layout as a JSON array of {@link HomeItem}. */
public final class HomeLayoutStore {

    private static final String FILE = "piko_layout";
    private static final String KEY_ITEMS = "items";

    private final SharedPreferences sp;

    private static HomeLayoutStore instance;

    private HomeLayoutStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized HomeLayoutStore get(Context context) {
        if (instance == null) instance = new HomeLayoutStore(context.getApplicationContext());
        return instance;
    }

    public List<HomeItem> loadAll() {
        List<HomeItem> result = new ArrayList<>();
        String raw = sp.getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                result.add(HomeItem.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public void saveAll(List<HomeItem> items) {
        JSONArray arr = new JSONArray();
        try {
            for (HomeItem item : items) arr.put(item.toJson());
        } catch (JSONException ignored) {
        }
        com.piko.launcher.util.Compat.apply(sp.edit().putString(KEY_ITEMS, arr.toString()));
    }

    public void clear() {
        com.piko.launcher.util.Compat.apply(sp.edit().remove(KEY_ITEMS));
    }
}
