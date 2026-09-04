package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Single access point for every Piko preference. Keeping keys and defaults
 * here avoids magic strings scattered across the UI layer.
 */
public final class Prefs {

    private static final String FILE = "piko_prefs";

    private static final String KEY_COLUMNS = "grid_columns";
    private static final String KEY_ROWS = "grid_rows";
    private static final String KEY_DEFAULT_PAGE = "default_page";
    private static final String KEY_DIRECT_CALL = "direct_call";
    private static final String KEY_GRID_PAGES = "grid_page_count";
    private static final String KEY_FIRST_RUN = "first_run_done";
    private static final String KEY_LAYOUT_REVISION = "layout_revision";
    private static final String KEY_CONTACTS_PERMISSION_ASKED = "contacts_permission_asked";
    /** Comma-separated engine ids in user-chosen order, enabled ones prefixed unmarked, disabled ones with a "-" prefix. */
    private static final String KEY_SEARCH_ENGINE_ORDER = "web_search_engine_order_v2";

    public static final int DEFAULT_COLUMNS = 5;
    public static final int DEFAULT_ROWS = 6;
    public static final int DEFAULT_PAGE_SEARCH = 0;
    public static final int DEFAULT_PAGE_HOME = 1;

    private final SharedPreferences sp;

    private static Prefs instance;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context context) {
        if (instance == null) instance = new Prefs(context.getApplicationContext());
        return instance;
    }

    public int columns() {
        return sp.getInt(KEY_COLUMNS, DEFAULT_COLUMNS);
    }

    public void setColumns(int value) {
        sp.edit().putInt(KEY_COLUMNS, value).apply();
    }

    public int rows() {
        return sp.getInt(KEY_ROWS, DEFAULT_ROWS);
    }

    public void setRows(int value) {
        sp.edit().putInt(KEY_ROWS, value).apply();
    }

    public int defaultPage() {
        return sp.getInt(KEY_DEFAULT_PAGE, DEFAULT_PAGE_SEARCH);
    }

    public void setDefaultPage(int page) {
        sp.edit().putInt(KEY_DEFAULT_PAGE, page).apply();
    }

    public boolean directCall() {
        return sp.getBoolean(KEY_DIRECT_CALL, false);
    }

    public void setDirectCall(boolean value) {
        sp.edit().putBoolean(KEY_DIRECT_CALL, value).apply();
    }

    public int gridPageCount() {
        return sp.getInt(KEY_GRID_PAGES, 1);
    }

    public void setGridPageCount(int count) {
        sp.edit().putInt(KEY_GRID_PAGES, count).apply();
    }

    /** Bumped whenever a change forces MainActivity to rebuild its grid pages (columns, rows, page count, layout reset). */
    public int layoutRevision() {
        return sp.getInt(KEY_LAYOUT_REVISION, 0);
    }

    public void bumpLayoutRevision() {
        sp.edit().putInt(KEY_LAYOUT_REVISION, layoutRevision() + 1).apply();
    }

    /** So the one-time "search my contacts" permission prompt never nags after the user answers it. */
    public boolean contactsPermissionAsked() {
        return sp.getBoolean(KEY_CONTACTS_PERMISSION_ASKED, false);
    }

    public void setContactsPermissionAsked(boolean value) {
        sp.edit().putBoolean(KEY_CONTACTS_PERMISSION_ASKED, value).apply();
    }

    /** Every catalog engine id, in the user's chosen order (defaults to catalog order if never touched). */
    public List<String> searchEngineOrder() {
        String raw = sp.getString(KEY_SEARCH_ENGINE_ORDER, null);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String token : raw.split(",")) {
                String id = token.startsWith("-") ? token.substring(1) : token;
                if (!id.isEmpty()) ids.add(id);
            }
        }
        for (WebSearchEngines.Engine e : WebSearchEngines.ALL) ids.add(e.id); // append any new catalog entries
        return new ArrayList<>(ids);
    }

    /** Subset of {@link #searchEngineOrder()} that the user left enabled, still in order. */
    public List<String> enabledSearchEngines() {
        String raw = sp.getString(KEY_SEARCH_ENGINE_ORDER, null);
        List<String> order = searchEngineOrder();
        if (raw == null || raw.isEmpty()) {
            List<String> result = new ArrayList<>();
            if (!order.isEmpty()) result.add(order.get(0)); // fresh install: only the first engine enabled
            return result;
        }
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            if (token.startsWith("-")) disabled.add(token.substring(1));
        }
        List<String> result = new ArrayList<>();
        for (String id : order) if (!disabled.contains(id)) result.add(id);
        return result;
    }

    public boolean isSearchEngineEnabled(String id) {
        return enabledSearchEngines().contains(id);
    }

    public void setSearchEngineEnabled(String id, boolean enabled) {
        List<String> order = searchEngineOrder();
        List<String> enabledNow = enabledSearchEngines();
        LinkedHashSet<String> enabledSet = new LinkedHashSet<>(enabledNow);
        if (enabled) enabledSet.add(id); else enabledSet.remove(id);
        saveEngineState(order, enabledSet);
    }

    public void setSearchEngineOrder(List<String> newOrder) {
        saveEngineState(newOrder, new LinkedHashSet<>(enabledSearchEngines()));
    }

    private void saveEngineState(List<String> order, LinkedHashSet<String> enabledSet) {
        StringBuilder sb = new StringBuilder();
        for (String id : order) {
            if (sb.length() > 0) sb.append(",");
            if (!enabledSet.contains(id)) sb.append("-");
            sb.append(id);
        }
        sp.edit().putString(KEY_SEARCH_ENGINE_ORDER, sb.toString()).apply();
    }

    public boolean isFirstRunDone() {
        return sp.getBoolean(KEY_FIRST_RUN, false);
    }

    public void markFirstRunDone() {
        sp.edit().putBoolean(KEY_FIRST_RUN, true).apply();
    }
}
