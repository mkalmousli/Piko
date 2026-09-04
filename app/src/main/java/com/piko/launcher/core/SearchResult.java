package com.piko.launcher.core;

/** One row in the unified search list: either an app or a contact, never both. */
public final class SearchResult {

    public static final int TYPE_APP = 0;
    public static final int TYPE_CONTACT = 1;
    public static final int TYPE_CALC = 2;
    public static final int TYPE_WEB = 3;

    public final int type;
    public final AppInfo app;
    public final ContactInfo contact;
    /** TYPE_CALC/TYPE_WEB only: the expression evaluated or the raw query to search for. */
    public String text;
    /** TYPE_CALC only: the computed value, ready to copy or use. */
    public String value;

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
}
