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

    /** Drawable for a pinned-shortcut kind; shared with the search rows so both look identical. */
    public static int iconForKind(String kind) {
        switch (kind) {
            case "call": return R.drawable.ic_call;
            case "sms": return R.drawable.ic_sms;
            case "wa": return R.drawable.ic_chat;
            case "wacall": return R.drawable.ic_call;
            case "contact": return R.drawable.ic_info;
            case "copy": return R.drawable.ic_copy;
            case "web": return R.drawable.ic_globe;
            case "addcontact": return R.drawable.ic_person_add;
            default: return R.drawable.ic_bolt;
        }
    }

    private static View chipIcon(Context context, HomeItem item, int iconRes) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_home_icon, null, false);
        ImageView icon = view.findViewById(R.id.icon);
        TextView label = view.findViewById(R.id.label);
        float d = context.getResources().getDisplayMetrics().density;
        int pad = (int) (8 * d);
        icon.setBackgroundResource(R.drawable.bg_shortcut_chip);
        icon.setPadding(pad, pad, pad, pad);
        icon.setImageResource(iconRes);
        label.setText(item.label);
        label.setVisibility(Prefs.get(context).hideAllLabels() ? View.GONE : View.VISIBLE);
        view.setTag(item);
        return view;
    }

    public static View createShortcut(Context context, HomeItem item) {
        return chipIcon(context, item, iconForKind(item.kind));
    }

    public static View createFolder(Context context, HomeItem item) {
        return chipIcon(context, item, R.drawable.ic_folder);
    }

    /** True when the label should be hidden: globally, or for this app specifically. */
    public static boolean applyLabelVisibility(Context context, HomeItem item) {
        return Prefs.get(context).hideAllLabels()
                || AppCustomizationStore.get(context).hidesLabel(item.packageName + "/" + item.activityName);
    }
}
