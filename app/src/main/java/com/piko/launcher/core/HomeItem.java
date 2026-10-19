package com.piko.launcher.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A single occupant of the home grid: either an app shortcut or a bound
 * AppWidget. Position is stored as cell coordinates plus a span so widgets
 * can occupy multiple cells.
 */
public final class HomeItem {

    public static final int TYPE_APP = 0;
    public static final int TYPE_WIDGET = 1;
    /** A pinned search action (call, message, web search, Piko action...) described by {@link #kind}/{@link #data}. */
    public static final int TYPE_SHORTCUT = 2;
    /** A second-level page: a titled group of apps ({@link #children} holds "pkg/activity" keys). */
    public static final int TYPE_FOLDER = 3;

    public int type;
    public int page;
    public int col;
    public int row;
    public int colSpan = 1;
    public int rowSpan = 1;
    /** Free-form position as a fraction of the page (negative = not set, use col/row). */
    public float fx = -1f, fy = -1f;
    /** Free-form size as page fractions; non-positive means "use colSpan/rowSpan". */
    public float fw = -1f, fh = -1f;

    // TYPE_APP
    public String packageName;
    public String activityName;
    public String label;

    // TYPE_SHORTCUT (label is the title)
    public String kind;
    public String data;

    // TYPE_FOLDER (label is the title)
    public java.util.List<String> children = new java.util.ArrayList<>();

    // TYPE_WIDGET
    public int appWidgetId = -1;

    public boolean occupies(int c, int r) {
        return c >= col && c < col + colSpan && r >= row && r < row + rowSpan;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("page", page);
        o.put("col", col);
        o.put("row", row);
        o.put("colSpan", colSpan);
        o.put("rowSpan", rowSpan);
        if (fx >= 0 && fy >= 0) {
            o.put("fx", (double) fx);
            o.put("fy", (double) fy);
        }
        if (fw > 0 && fh > 0) {
            o.put("fw", (double) fw);
            o.put("fh", (double) fh);
        }
        if (type == TYPE_APP) {
            o.put("pkg", packageName);
            o.put("act", activityName);
            o.put("label", label);
        } else if (type == TYPE_SHORTCUT) {
            o.put("label", label);
            o.put("kind", kind);
            o.put("data", data);
        } else if (type == TYPE_FOLDER) {
            o.put("label", label);
            o.put("children", new org.json.JSONArray(children));
        } else {
            o.put("widgetId", appWidgetId);
        }
        return o;
    }

    static HomeItem fromJson(JSONObject o) throws JSONException {
        HomeItem item = new HomeItem();
        item.type = o.optInt("type");
        item.page = o.optInt("page");
        item.col = o.optInt("col");
        item.row = o.optInt("row");
        item.colSpan = o.optInt("colSpan", 1);
        item.rowSpan = o.optInt("rowSpan", 1);
        item.fx = (float) o.optDouble("fx", -1);
        item.fy = (float) o.optDouble("fy", -1);
        item.fw = (float) o.optDouble("fw", -1);
        item.fh = (float) o.optDouble("fh", -1);
        if (item.type == TYPE_APP) {
            item.packageName = o.optString("pkg");
            item.activityName = o.optString("act");
            item.label = o.optString("label");
        } else if (item.type == TYPE_SHORTCUT) {
            item.label = o.optString("label");
            item.kind = o.optString("kind");
            item.data = o.optString("data");
        } else if (item.type == TYPE_FOLDER) {
            item.label = o.optString("label");
            org.json.JSONArray arr = o.optJSONArray("children");
            for (int i = 0; arr != null && i < arr.length(); i++) item.children.add(arr.optString(i));
        } else {
            item.appWidgetId = o.optInt("widgetId", -1);
        }
        return item;
    }
}
