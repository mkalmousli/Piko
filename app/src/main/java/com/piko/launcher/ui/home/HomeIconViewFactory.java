package com.piko.launcher.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.AppCustomizationStore;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.HomeItem;
import com.piko.launcher.core.IconCache;

/** Builds the app-icon child views placed inside a {@link HomeGridLayout}. */
public final class HomeIconViewFactory {

    private HomeIconViewFactory() {
    }

    public static View createAppIcon(Context context, HomeItem item) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_home_icon, null, false);
        ImageView icon = view.findViewById(R.id.icon);
        TextView label = view.findViewById(R.id.label);
        String key = item.packageName + "/" + item.activityName;
        AppCustomizationStore store = AppCustomizationStore.get(context);
        AppCustomizationStore.Entry custom = store.get(key);
        label.setText(custom != null && custom.label != null && !custom.label.trim().isEmpty() ? custom.label : item.label);
        label.setVisibility(applyLabelVisibility(context, item) ? View.GONE : View.VISIBLE);

        int sizePx = (int) (36 * context.getResources().getDisplayMetrics().density);
        AppInfo synthetic = new AppInfo(item.packageName, item.activityName, item.label);
        IconCache.get(context).load(synthetic, icon, sizePx);

        view.setTag(item);
        return view;
    }

    /** True when the label should be hidden: globally, or for this app specifically. */
    public static boolean applyLabelVisibility(Context context, HomeItem item) {
        return Prefs.get(context).hideAllLabels()
                || AppCustomizationStore.get(context).hidesLabel(item.packageName + "/" + item.activityName);
    }
}
