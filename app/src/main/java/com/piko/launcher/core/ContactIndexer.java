package com.piko.launcher.core;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;

import com.piko.launcher.util.PermissionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds and maintains the in-memory phone-contact index so search can match
 * people by name, not just apps. Loading is a no-op (empty result) until
 * READ_CONTACTS is granted; callers just re-invoke {@link #loadAsync} once
 * the permission comes through.
 */
public final class ContactIndexer {

    public interface Listener {
        void onContactsReady(List<ContactInfo> contacts);
    }

    private static ContactIndexer instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<ContactInfo> contacts = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    private ContactIndexer(Context context) {
        this.appContext = context.getApplicationContext();
        registerContentObserver();
    }

    public static synchronized ContactIndexer get(Context context) {
        if (instance == null) instance = new ContactIndexer(context);
        return instance;
    }

    public List<ContactInfo> snapshot() {
        return new ArrayList<>(contacts);
    }

    public void addListener(Listener l) {
        listeners.add(l);
        if (loaded.get()) l.onContactsReady(snapshot());
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void loadAsync() {
        if (!PermissionUtil.has(appContext, android.Manifest.permission.READ_CONTACTS)) {
            contacts.clear();
            loaded.set(true);
            notifyListeners();
            return;
        }
        executor.execute(() -> {
            List<ContactInfo> fresh = queryAllContacts();
            contacts.clear();
            contacts.addAll(fresh);
            loaded.set(true);
            notifyListeners();
        });
    }

    private List<ContactInfo> queryAllContacts() {
        Map<Long, ContactInfo> byContactId = new LinkedHashMap<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = appContext.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    if (byContactId.containsKey(id)) continue; // keep first (primary-ish) number only
                    String name = cursor.getString(1);
                    String number = cursor.getString(2);
                    if (name == null || number == null) continue;
                    byContactId.put(id, new ContactInfo(id, name, number));
                }
            }
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the query; treat as empty.
        }
        return new ArrayList<>(byContactId.values());
    }

    private void notifyListeners() {
        List<ContactInfo> snap = snapshot();
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onContactsReady(snap);
        });
    }

    private void registerContentObserver() {
        appContext.getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true,
                new ContentObserver(mainHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        loadAsync();
                    }
                });
    }
}
