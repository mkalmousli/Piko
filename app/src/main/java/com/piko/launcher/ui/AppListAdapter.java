package com.piko.launcher.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.IconCache;

import java.util.ArrayList;
import java.util.List;

/** Lightweight BaseAdapter for the search results ListView — no RecyclerView needed. */
public final class AppListAdapter extends BaseAdapter {

    private final Context context;
    private final IconCache iconCache;
    private final int iconSizePx;
    private List<AppInfo> items = new ArrayList<>();

    public AppListAdapter(Context context) {
        this.context = context;
        this.iconCache = IconCache.get(context);
        this.iconSizePx = (int) (40 * context.getResources().getDisplayMetrics().density);
    }

    public void submit(List<AppInfo> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public AppInfo getAppAt(int position) {
        return items.get(position);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        Holder holder;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_app, parent, false);
            holder = new Holder();
            holder.icon = view.findViewById(R.id.icon);
            holder.label = view.findViewById(R.id.label);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        AppInfo app = items.get(position);
        holder.label.setText(app.label);
        iconCache.load(app, holder.icon, iconSizePx);
        return view;
    }

    private static final class Holder {
        ImageView icon;
        TextView label;
    }
}
