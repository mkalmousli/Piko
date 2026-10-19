package com.piko.launcher.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.AppInfo;
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
        label.setText(item.label);

        int sizePx = (int) (36 * context.getResources().getDisplayMetrics().density);
        AppInfo synthetic = new AppInfo(item.packageName, item.activityName, item.label);
        IconCache.get(context).load(synthetic, icon, sizePx);

        view.setTag(item);
        return view;
    }
}
