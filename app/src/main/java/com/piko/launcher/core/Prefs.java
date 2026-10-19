package com.piko.launcher.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
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
    private static final String KEY_DEFAULT_PAGE = "default_grid_page";
    private static final String KEY_DIRECT_CALL = "direct_call";
    private static final String KEY_GRID_PAGES = "grid_page_count";
    private static final String KEY_FIRST_RUN = "first_run_done";
    private static final String KEY_LAYOUT_REVISION = "layout_revision";
    private static final String KEY_CONTACTS_PERMISSION_ASKED = "contacts_permission_asked";
    /** Comma-separated engine ids in user-chosen order, enabled ones prefixed unmarked, disabled ones with a "-" prefix. */
    private static final String KEY_SEARCH_ENGINE_ORDER = "web_search_engine_order_v2";

    private static final String KEY_CUSTOM_ENGINES = "custom_search_engines";
    private static final String KEY_ICON_PACK = "icon_pack";
    private static final String KEY_HIDDEN_APPS = "hidden_apps";
    private static final String KEY_CONTACT_TAP = "contact_tap_action";

    public static final int DEFAULT_COLUMNS = 5;
    public static final int DEFAULT_ROWS = 6;

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

    public static final int BG_DEFAULT = 0, BG_COLOR = 1, BG_GRADIENT = 2, BG_WALLPAPER = 3, BG_IMAGE = 4;

    public int bgMode() { return sp.getInt("bg_mode", BG_DEFAULT); }
    public int bgColor1() { return sp.getInt("bg_color1", 0xFF0E1015); }
    public int bgColor2() { return sp.getInt("bg_color2", 0xFF2A3A7A); }
    /** Gradient direction: 0 top-bottom, 1 diagonal, 2 left-right, 3 reverse diagonal. */
    public int bgAngle() { return sp.getInt("bg_angle", 0); }
    public String bgImage() { return sp.getString("bg_image", null); }
    /** Darkening over the background, 0-70 (percent). */
    public int bgDim() { return sp.getInt("bg_dim", 0); }

    public void setBackground(int mode, int c1, int c2, int angle, String image, int dim) {
        sp.edit().putInt("bg_mode", mode).putInt("bg_color1", c1).putInt("bg_color2", c2)
                .putInt("bg_angle", angle).putString("bg_image", image).putInt("bg_dim", dim).apply();
    }

    public int defaultPage() {
        return sp.getInt(KEY_DEFAULT_PAGE, 0);
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

    /** Package name of the selected icon pack, or "" for the system icons. */
    public String iconPack() {
        return sp.getString(KEY_ICON_PACK, "");
    }

    public void setIconPack(String pkg) {
        sp.edit().putString(KEY_ICON_PACK, pkg == null ? "" : pkg).apply();
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
        for (WebSearchEngines.Engine e : allEngines()) ids.add(e.id); // append any new catalog entries
        List<String> result = new ArrayList<>();
        for (String id : ids) if (engineExists(id)) result.add(id);
        return result;
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
        // Ids never saved before (newly added catalog entries) start disabled.
        LinkedHashSet<String> enabledSaved = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            if (!token.isEmpty() && !token.startsWith("-")) enabledSaved.add(token);
        }
        List<String> result = new ArrayList<>();
        for (String id : order) if (enabledSaved.contains(id)) result.add(id);
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


    // ------------------------------------------------------- engine catalog

    public List<WebSearchEngines.Engine> customEngines() {
        List<WebSearchEngines.Engine> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_CUSTOM_ENGINES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new WebSearchEngines.Engine(o.getString("id"), o.getString("label"), o.getString("url")));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public List<WebSearchEngines.Engine> allEngines() {
        List<WebSearchEngines.Engine> list = new ArrayList<>(WebSearchEngines.ALL);
        list.addAll(customEngines());
        return list;
    }

    private boolean engineExists(String id) {
        for (WebSearchEngines.Engine e : allEngines()) if (e.id.equals(id)) return true;
        return false;
    }

    public WebSearchEngines.Engine engineById(String id) {
        for (WebSearchEngines.Engine e : allEngines()) if (e.id.equals(id)) return e;
        return WebSearchEngines.ALL.get(0);
    }

    public boolean isCustomEngine(String id) {
        return WebSearchEngines.builtIn(id) == null;
    }

    public void addCustomEngine(String label, String url) {
        List<WebSearchEngines.Engine> list = customEngines();
        String id = "custom_" + System.currentTimeMillis();
        list.add(new WebSearchEngines.Engine(id, label, url));
        saveCustomEngines(list);
        setSearchEngineEnabled(id, true);
    }

    public void removeCustomEngine(String id) {
        List<WebSearchEngines.Engine> list = customEngines();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        saveCustomEngines(list);
    }

    private void saveCustomEngines(List<WebSearchEngines.Engine> list) {
        JSONArray arr = new JSONArray();
        try {
            for (WebSearchEngines.Engine e : list) {
                arr.put(new JSONObject().put("id", e.id).put("label", e.label).put("url", e.urlPrefix));
            }
        } catch (JSONException ignored) {
        }
        sp.edit().putString(KEY_CUSTOM_ENGINES, arr.toString()).apply();
    }

    // ------------------------------------------------------ search behaviour

    public static final String FLAG_SEARCH_APPS = "search_apps";
    public static final String FLAG_SEARCH_CONTACTS = "search_contacts";
    public static final String FLAG_SEARCH_CALC = "search_calc";
    public static final String FLAG_SEARCH_CURRENCY = "search_currency";
    public static final String FLAG_SEARCH_WEB = "search_web";
    public static final String FLAG_QUICK_ACTIONS = "search_quick_actions";
    public static final String FLAG_ACTION_CALL = "contact_action_call";
    public static final String FLAG_ACTION_SMS = "contact_action_sms";
    public static final String FLAG_ACTION_WA_MESSAGE = "contact_action_wa_message";
    public static final String FLAG_ACTION_WA_CALL = "contact_action_wa_call";

    public static final String TAP_OPEN = "open";
    public static final String TAP_CALL = "call";
    public static final String TAP_COPY = "copy";
    public static final String TAP_NONE = "none";

    /** Boolean toggles for search sources and contact action buttons; every one defaults to on. */
    private static final String KEY_CALC_HISTORY = "calc_history";
    private static final int HISTORY_MAX = 40;

    /** Most recent first; each entry is {expression, value}. */
    public java.util.List<String[]> calcHistory() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_CALC_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONArray e = arr.getJSONArray(i);
                out.add(new String[]{e.getString(0), e.getString(1)});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void addCalcHistory(String expression, String value) {
        java.util.List<String[]> list = calcHistory();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i)[0].equals(expression) && list.get(i)[1].equals(value)) list.remove(i);
        }
        list.add(0, new String[]{expression, value});
        saveCalcHistory(list.size() > HISTORY_MAX ? list.subList(0, HISTORY_MAX) : list);
    }

    public void clearCalcHistory() {
        sp.edit().remove(KEY_CALC_HISTORY).apply();
    }

    private void saveCalcHistory(java.util.List<String[]> list) {
        JSONArray arr = new JSONArray();
        for (String[] e : list) arr.put(new JSONArray().put(e[0]).put(e[1]));
        sp.edit().putString(KEY_CALC_HISTORY, arr.toString()).apply();
    }

    public boolean flag(String key) {
        return sp.getBoolean(key, true);
    }

    public void setFlag(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }

    public String contactTapAction() {
        return sp.getString(KEY_CONTACT_TAP, TAP_OPEN);
    }

    public void setContactTapAction(String value) {
        sp.edit().putString(KEY_CONTACT_TAP, value).apply();
    }

    public Set<String> hiddenApps() {
        return new HashSet<>(sp.getStringSet(KEY_HIDDEN_APPS, new HashSet<>()));
    }

    public void setAppHidden(String appKey, boolean hidden) {
        Set<String> set = hiddenApps();
        if (hidden) set.add(appKey); else set.remove(appKey);
        sp.edit().putStringSet(KEY_HIDDEN_APPS, set).apply();
    }

    /** Free-form placement (default on): icons sit exactly where dropped instead of snapping to cells. */
    public boolean freeform() {
        return sp.getBoolean("freeform", true);
    }

    public void setFreeform(boolean v) {
        sp.edit().putBoolean("freeform", v).apply();
    }

    public boolean hideAllLabels() {
        return sp.getBoolean("hide_all_labels", false);
    }

    public void setHideAllLabels(boolean v) {
        sp.edit().putBoolean("hide_all_labels", v).apply();
    }

    /** Show the keyboard automatically when the search page is in front (default on). */
    public boolean autoFocusKeyboard() {
        return sp.getBoolean("autofocus_keyboard", true);
    }

    public void setAutoFocusKeyboard(boolean v) {
        sp.edit().putBoolean("autofocus_keyboard", v).apply();
    }

    public boolean isFirstRunDone() {
        return sp.getBoolean(KEY_FIRST_RUN, false);
    }

    public void markFirstRunDone() {
        sp.edit().putBoolean(KEY_FIRST_RUN, true).apply();
    }
}
