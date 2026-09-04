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
public final class WidgetHostController {

    private static final int HOST_ID = 0x504B; // "PK"

    private static WidgetHostController instance;

    private final AppWidgetHost host;
    private final AppWidgetManager manager;

    private WidgetHostController(Context context) {
        Context app = context.getApplicationContext();
        host = new AppWidgetHost(app, HOST_ID);
        manager = AppWidgetManager.getInstance(app);
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
        return host.allocateAppWidgetId();
    }

    public void deleteAppWidgetId(int appWidgetId) {
        host.deleteAppWidgetId(appWidgetId);
    }

    public AppWidgetProviderInfo infoFor(int appWidgetId) {
        return manager.getAppWidgetInfo(appWidgetId);
    }

    public AppWidgetHostView createView(Context context, int appWidgetId, AppWidgetProviderInfo info) {
        return host.createView(context, appWidgetId, info);
    }

    public void startListening() {
        try {
            host.startListening();
        } catch (Exception ignored) {
        }
    }

    public void stopListening() {
        try {
            host.stopListening();
        } catch (Exception ignored) {
        }
    }
}
