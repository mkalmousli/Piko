package com.piko.launcher.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A saved quick-contact entry shown as a dock chip. {@code actions} is a
 * bitmask of {@link #ACTION_CALL}, {@link #ACTION_WA_MESSAGE},
 * {@link #ACTION_WA_CALL} and {@link #ACTION_WA_VIDEO}.
 */
public final class ShortcutContact {

    public static final int ACTION_CALL = 1;
    public static final int ACTION_SMS = 1 << 1;
    public static final int ACTION_WA_MESSAGE = 1 << 2;
    public static final int ACTION_WA_CALL = 1 << 3;

    public static final int ACTION_ALL = ACTION_CALL | ACTION_SMS | ACTION_WA_MESSAGE | ACTION_WA_CALL;

    public long id;
    public String name;
    public String phoneNumber;
    public String photoUri;
    public int actions;

    public ShortcutContact() {
    }

    public ShortcutContact(long id, String name, String phoneNumber, String photoUri, int actions) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.photoUri = photoUri;
        this.actions = actions;
    }

    public boolean has(int action) {
        return (actions & action) != 0;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("phone", phoneNumber);
        o.put("photo", photoUri == null ? "" : photoUri);
        o.put("actions", actions);
        return o;
    }

    static ShortcutContact fromJson(JSONObject o) throws JSONException {
        ShortcutContact c = new ShortcutContact();
        c.id = o.optLong("id");
        c.name = o.optString("name");
        c.phoneNumber = o.optString("phone");
        c.photoUri = o.optString("photo", "");
        c.actions = o.optInt("actions");
        return c;
    }
}
