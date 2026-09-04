package com.piko.launcher.ui.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** All the widgets offered by one installed app, for the grouped/expandable widget picker. */
public final class WidgetProviderGroup {

    public final String packageName;
    public final String appLabel;
    public final Drawable appIcon;
    public final List<AppWidgetProviderInfo> widgets = new ArrayList<>();

    public WidgetProviderGroup(String packageName, String appLabel, Drawable appIcon) {
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.appIcon = appIcon;
    }
}
