package com.piko.launcher.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds and maintains the in-memory app index. Loading happens once on a
 * background thread; afterwards a package-change receiver keeps it fresh
 * incrementally so re-indexing never blocks the UI thread.
 */
public final class AppIndexer {

    public interface Listener {
        void onIndexReady(List<AppInfo> apps);
    }

    private static AppIndexer instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<AppInfo> apps = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    private AppIndexer(Context context) {
        this.appContext = context.getApplicationContext();
        registerPackageReceiver();
    }

    public static synchronized AppIndexer get(Context context) {
        if (instance == null) instance = new AppIndexer(context);
        return instance;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public List<AppInfo> snapshot() {
        return new ArrayList<>(apps);
    }

    public void addListener(Listener l) {
        listeners.add(l);
        if (loaded.get()) l.onIndexReady(snapshot());
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void loadAsync() {
        executor.execute(() -> {
            List<AppInfo> fresh = queryAllApps();
            apps.clear();
            apps.addAll(fresh);
            loaded.set(true);
            notifyListeners();
        });
    }

    private List<AppInfo> queryAllApps() {
        PackageManager pm = appContext.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);

        List<AppInfo> result = new ArrayList<>(resolved.size());
        for (ResolveInfo ri : resolved) {
            String label = ri.loadLabel(pm).toString();
            AppInfo info = new AppInfo(ri.activityInfo.packageName, ri.activityInfo.name, label);
            result.add(info);
        }
        Collections.sort(result, Comparator.comparing(a -> a.labelLower));
        return result;
    }

    private void notifyListeners() {
        List<AppInfo> snap = snapshot();
        mainHandler.post(() -> {
            for (Listener l : listeners) l.onIndexReady(snap);
        });
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        appContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadAsync();
            }
        }, filter);
    }
}
