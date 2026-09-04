package com.piko.launcher.core;

/** One row in the unified search list: either an app or a contact, never both. */
public final class SearchResult {

    public static final int TYPE_APP = 0;
    public static final int TYPE_CONTACT = 1;

    public final int type;
    public final AppInfo app;
    public final ContactInfo contact;

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
}
