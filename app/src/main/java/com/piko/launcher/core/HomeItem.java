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

    public int type;
    public int page;
    public int col;
    public int row;
    public int colSpan = 1;
    public int rowSpan = 1;

    // TYPE_APP
    public String packageName;
    public String activityName;
    public String label;

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
        if (type == TYPE_APP) {
            o.put("pkg", packageName);
            o.put("act", activityName);
            o.put("label", label);
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
        if (item.type == TYPE_APP) {
            item.packageName = o.optString("pkg");
            item.activityName = o.optString("act");
            item.label = o.optString("label");
        } else {
            item.appWidgetId = o.optInt("widgetId", -1);
        }
        return item;
    }
}
