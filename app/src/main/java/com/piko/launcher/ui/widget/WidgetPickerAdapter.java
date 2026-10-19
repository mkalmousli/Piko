package com.piko.launcher.ui.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.piko.launcher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Groups installed widget providers by owning app; each child row shows a live preview. */
@android.annotation.TargetApi(16)
public final class WidgetPickerAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private List<WidgetProviderGroup> groups = new ArrayList<>();
    private final ConcurrentHashMap<String, String> labels = new ConcurrentHashMap<>();
    private final com.piko.launcher.util.LruCache<String, Drawable> previews = new com.piko.launcher.util.LruCache<>(48);
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());

    public WidgetPickerAdapter(Context context) {
        this.context = context;
    }

    /** Widget label, loaded once per provider (safe to call from any thread). */
    public String label(AppWidgetProviderInfo info) {
        String key = info.provider.flattenToString();
        String l = labels.get(key);
        if (l == null) {
            try {
                l = android.os.Build.VERSION.SDK_INT >= 21 ? info.loadLabel(context.getPackageManager()) : info.label;
            } catch (Exception e) {
                l = info.provider.getShortClassName();
            }
            if (l == null) l = info.provider.getShortClassName();
            labels.put(key, l);
        }
        return l;
    }

    public void shutdown() {
        loader.shutdownNow();
    }

    public void submit(List<WidgetProviderGroup> newGroups) {
        this.groups = newGroups;
        notifyDataSetChanged();
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return groups.get(groupPosition).widgets.size();
    }

    @Override
    public WidgetProviderGroup getGroup(int groupPosition) {
        return groups.get(groupPosition);
    }

    @Override
    public AppWidgetProviderInfo getChild(int groupPosition, int childPosition) {
        return groups.get(groupPosition).widgets.get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupPosition * 10000L + childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_widget_group, parent, false);
        }
        WidgetProviderGroup group = groups.get(groupPosition);
        ((ImageView) view.findViewById(R.id.app_icon)).setImageDrawable(group.appIcon);
        ((TextView) view.findViewById(R.id.app_label)).setText(group.appLabel);
        int count = group.widgets.size();
        ((TextView) view.findViewById(R.id.widget_count)).setText(String.valueOf(count));
        return view;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_widget_child, parent, false);
        }
        AppWidgetProviderInfo info = groups.get(groupPosition).widgets.get(childPosition);
        ImageView preview = view.findViewById(R.id.preview);
        TextView label = view.findViewById(R.id.widget_label);
        TextView size = view.findViewById(R.id.widget_size);

        label.setText(label(info));
        size.setText(info.minWidth + "×" + info.minHeight + " dp");
        final String key = info.provider.flattenToString();
        preview.setTag(key);
        Drawable cached = previews.get(key);
        if (cached != null) {
            preview.setImageDrawable(cached);
        } else {
            preview.setImageDrawable(null);
            loader.execute(() -> {
                Drawable d = loadPreview(info);
                previews.put(key, d);
                main.post(() -> {
                    if (key.equals(preview.getTag())) preview.setImageDrawable(d);
                });
            });
        }
        return view;
    }

    private Drawable loadPreview(AppWidgetProviderInfo info) {
        Drawable preview = null;
        try {
            preview = android.os.Build.VERSION.SDK_INT >= 21 ? info.loadPreviewImage(context, 0)
                    : (info.previewImage == 0 ? null
                    : context.getPackageManager().getDrawable(info.provider.getPackageName(), info.previewImage, null));
        } catch (Exception ignored) {
        }
        if (preview == null) {
            try {
                preview = android.os.Build.VERSION.SDK_INT >= 21 ? info.loadIcon(context, 0)
                        : (info.icon == 0 ? null
                        : context.getPackageManager().getDrawable(info.provider.getPackageName(), info.icon, null));
            } catch (Exception ignored) {
            }
        }
        if (preview == null) {
            preview = com.piko.launcher.util.Compat.drawable(context.getResources(), R.drawable.ic_placeholder_app);
        }
        return preview;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
