package com.piko.launcher.ui.widget;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

/**
 * Thin wrapper around {@link AppWidgetHost} — the pure-framework API every
 * launcher uses to host third-party widgets (no library involved). One host
 * instance is shared for the process; its id is fixed so re-launches keep
 * talking to the same widget bindings.
 */
@android.annotation.TargetApi(16)
public final class WidgetHostController {

    /** Hosting widgets needs the bind API of Android 4.1; older versions simply get no widget support. */
    public static final boolean SUPPORTED = android.os.Build.VERSION.SDK_INT >= 16;

    private static final int HOST_ID = 0x504B; // "PK"

    private static WidgetHostController instance;

    private final AppWidgetHost host;
    private final AppWidgetManager manager;

    private WidgetHostController(Context context) {
        Context app = context.getApplicationContext();
        host = SUPPORTED ? new AppWidgetHost(app, HOST_ID) : null;
        manager = SUPPORTED ? AppWidgetManager.getInstance(app) : null;
    }

    public static synchronized WidgetHostController get(Context context) {
        if (instance == null) instance = new WidgetHostController(context);
        return instance;
    }

    public AppWidgetHost host() {
        return host;
    }

    public AppWidgetManager manager() {
        return manager;
    }

    public int allocateAppWidgetId() {
        return SUPPORTED ? host.allocateAppWidgetId() : -1;
    }

    public void deleteAppWidgetId(int appWidgetId) {
        if (SUPPORTED) host.deleteAppWidgetId(appWidgetId);
    }

    public AppWidgetProviderInfo infoFor(int appWidgetId) {
        return SUPPORTED ? manager.getAppWidgetInfo(appWidgetId) : null;
    }

    public AppWidgetHostView createView(Context context, int appWidgetId, AppWidgetProviderInfo info) {
        return SUPPORTED ? host.createView(context, appWidgetId, info) : null;
    }

    public void startListening() {
        try {
            if (SUPPORTED) host.startListening();
        } catch (Exception ignored) {
        }
    }

    public void stopListening() {
        try {
            if (SUPPORTED) host.stopListening();
        } catch (Exception ignored) {
        }
    }
}
