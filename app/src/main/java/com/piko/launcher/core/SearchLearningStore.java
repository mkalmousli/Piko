package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Remembers which result the user actually picked for a given typed query,
 * so next time that exact query is typed the previously chosen app/contact
 * jumps back to the top — search results improve with use instead of always
 * ranking the same way.
 */
public final class SearchLearningStore {

    private static final String FILE = "piko_search_learning";
    private static final int MAX_ENTRIES_PER_QUERY = 6;

    private final SharedPreferences sp;

    private static SearchLearningStore instance;

    private SearchLearningStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized SearchLearningStore get(Context context) {
        if (instance == null) instance = new SearchLearningStore(context.getApplicationContext());
        return instance;
    }

    public void recordSelection(String query, String itemKey) {
        String normalized = normalize(query);
        if (com.piko.launcher.util.Compat.isEmpty(normalized) || itemKey == null) return;

        JSONObject counts = loadCounts(normalized);
        int current = counts.optInt(itemKey, 0);
        try {
            counts.put(itemKey, current + 1);
            trimIfNeeded(counts);
            com.piko.launcher.util.Compat.apply(sp.edit().putString(prefKey(normalized), counts.toString()));
        } catch (JSONException ignored) {
        }
    }

    public int boostFor(String query, String itemKey) {
        String normalized = normalize(query);
        if (com.piko.launcher.util.Compat.isEmpty(normalized) || itemKey == null) return 0;
        return loadCounts(normalized).optInt(itemKey, 0);
    }

    private JSONObject loadCounts(String normalizedQuery) {
        String raw = sp.getString(prefKey(normalizedQuery), null);
        if (raw == null) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void trimIfNeeded(JSONObject counts) {
        while (counts.length() > MAX_ENTRIES_PER_QUERY) {
            String weakestKey = null;
            int weakestCount = Integer.MAX_VALUE;
            Iterator<String> keys = counts.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int count = counts.optInt(key, 0);
                if (count < weakestCount) {
                    weakestCount = count;
                    weakestKey = key;
                }
            }
            if (weakestKey == null) break;
            counts.remove(weakestKey);
        }
    }

    private String prefKey(String normalizedQuery) {
        return "q_" + normalizedQuery;
    }

    private String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }
}
