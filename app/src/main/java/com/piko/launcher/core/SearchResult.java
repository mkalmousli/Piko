package com.piko.launcher.core;

/** One row in the unified search list: either an app or a contact, never both. */
public final class SearchResult {

    public static final int TYPE_APP = 0;
    public static final int TYPE_CONTACT = 1;
    public static final int TYPE_CALC = 2;
    public static final int TYPE_WEB = 3;
    /** One tappable action (call, message, one web engine…) shown as its own row. */
    public static final int TYPE_ACTION = 4;
    /** A strip of compact action buttons belonging to the group header above it. */
    public static final int TYPE_CHIPS = 5;

    public static final class Chip {
        public int icon;
        public String badge, label;
        public int color;
        public Runnable run;
        public LongPress onLongPress;

        public interface LongPress {
            void accept(android.view.View v);
        }
    }

    public java.util.List<Chip> chips;
    public String chipLabel;

    public SearchResult chipLabel(String label) {
        chipLabel = label;
        return this;
    }

    public static SearchResult forChips(java.util.List<Chip> chips) {
        SearchResult r = new SearchResult(TYPE_CHIPS, null, null);
        r.chips = chips;
        return r;
    }

    public final int type;
    public final AppInfo app;
    public final ContactInfo contact;
    /** TYPE_CALC/TYPE_WEB only: the expression evaluated or the raw query to search for. */
    public String text;
    /** TYPE_CALC only: the computed value, ready to copy or use. */
    public String value;
    /** TYPE_ACTION only: badge glyph, subtitle and what tapping does. */
    public String badge, subtitle;
    public Runnable action;
    /** TYPE_ACTION only: drawable resource for the badge (0 = use the badge text). */
    public int icon;
    /** TYPE_ACTION only: how to rebuild this row as a home shortcut (see {@link HomeItem}). Null = not pinnable. */
    public String shortcutKind, shortcutData;

    /** Grouping: header rows toggle their children; children are drawn indented under an open header. */
    public static final int GROUP_NONE = 0, GROUP_HEADER = 1, GROUP_OPEN = 2, GROUP_CHILD = 3, GROUP_FIXED = 4;
    public int group = GROUP_NONE;
    /** Copied on long-press when set (e.g. a contact's number). */
    public String copyText;

    /** Group membership: the header and its chip strip share a key; {@code groupFixed} groups never collapse. */
    public String groupKey;
    public boolean groupFixed;

    public SearchResult grouped(int g) {
        group = g;
        return this;
    }

    public SearchResult copyOnLongPress(String text) {
        copyText = text;
        return this;
    }

    public SearchResult withIcon(int res) {
        icon = res;
        return this;
    }

    public SearchResult pinnable(String kind, String data) {
        shortcutKind = kind;
        shortcutData = data;
        return this;
    }
    /** TYPE_ACTION only: the contact this row belongs to, so a tap can teach the ranker. */
    public ContactInfo owner;

    private SearchResult(int type, AppInfo app, ContactInfo contact) {
        this.type = type;
        this.app = app;
        this.contact = contact;
    }

    public static SearchResult forApp(AppInfo app) {
        return new SearchResult(TYPE_APP, app, null);
    }

    public static SearchResult forContact(ContactInfo contact) {
        return new SearchResult(TYPE_CONTACT, null, contact);
    }

    public static SearchResult forCalc(String expression, String value) {
        SearchResult r = new SearchResult(TYPE_CALC, null, null);
        r.text = expression;
        r.value = value;
        return r;
    }

    public static SearchResult forWeb(String query) {
        SearchResult r = new SearchResult(TYPE_WEB, null, null);
        r.text = query;
        return r;
    }

    public static SearchResult forAction(String badge, String title, String subtitle, Runnable action) {
        SearchResult r = new SearchResult(TYPE_ACTION, null, null);
        r.badge = badge;
        r.text = title;
        r.subtitle = subtitle;
        r.action = action;
        return r;
    }
}
