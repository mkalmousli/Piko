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

/** Groups installed widget providers by owning app; each child row shows a live preview. */
public final class WidgetPickerAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private List<WidgetProviderGroup> groups = new ArrayList<>();

    public WidgetPickerAdapter(Context context) {
        this.context = context;
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

        label.setText(info.loadLabel(context.getPackageManager()));
        size.setText(info.minWidth + "×" + info.minHeight + " dp");
        preview.setImageDrawable(loadPreview(info));
        return view;
    }

    private Drawable loadPreview(AppWidgetProviderInfo info) {
        Drawable preview = null;
        try {
            preview = info.loadPreviewImage(context, 0);
        } catch (Exception ignored) {
        }
        if (preview == null) {
            try {
                preview = info.loadIcon(context, 0);
            } catch (Exception ignored) {
            }
        }
        if (preview == null) {
            preview = context.getDrawable(R.drawable.ic_placeholder_app);
        }
        return preview;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
