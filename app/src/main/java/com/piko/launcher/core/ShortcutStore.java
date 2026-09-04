package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the user's quick-contact shortcuts as a JSON array in
 * SharedPreferences. org.json ships inside the Android platform itself, so
 * this stays dependency-free.
 */
public final class ShortcutStore {

    private static final String FILE = "piko_shortcuts";
    private static final String KEY_LIST = "contacts";

    private final SharedPreferences sp;

    private static ShortcutStore instance;

    private ShortcutStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized ShortcutStore get(Context context) {
        if (instance == null) instance = new ShortcutStore(context.getApplicationContext());
        return instance;
    }

    public List<ShortcutContact> loadAll() {
        List<ShortcutContact> result = new ArrayList<>();
        String raw = sp.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                result.add(ShortcutContact.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    public void saveAll(List<ShortcutContact> contacts) {
        JSONArray arr = new JSONArray();
        try {
            for (ShortcutContact c : contacts) arr.put(c.toJson());
        } catch (JSONException ignored) {
        }
        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public void add(ShortcutContact contact) {
        List<ShortcutContact> all = loadAll();
        all.add(contact);
        saveAll(all);
    }

    public void remove(long contactId) {
        List<ShortcutContact> all = loadAll();
        List<ShortcutContact> next = new ArrayList<>();
        for (ShortcutContact c : all) if (c.id != contactId) next.add(c);
        saveAll(next);
    }
}
