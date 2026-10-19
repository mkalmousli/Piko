package com.piko.launcher.util;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.PopupMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A popup menu anchored to a view; a plain list dialog on Android before 3.0, which has no PopupMenu. */
public final class Popup {
    public static final class Item {
        final int id, order;
        final CharSequence title;

        Item(int id, int order, CharSequence title) {
            this.id = id;
            this.order = order;
            this.title = title;
        }

        public int getItemId() {
            return id;
        }
    }

    public interface Listener {
        boolean onMenuItemClick(Item item);
    }

    public final class Items {
        public void add(int group, int id, int order, int titleRes) {
            add(group, id, order, context.getString(titleRes));
        }

        public void add(int group, int id, int order, CharSequence title) {
            items.add(new Item(id, order, title));
        }
    }

    private final Context context;
    private final View anchor;
    private final List<Item> items = new ArrayList<>();
    private final Items menu = new Items();
    private Listener listener;

    public Popup(Context context, View anchor) {
        this.context = context;
        this.anchor = anchor;
    }

    public Items getMenu() {
        return menu;
    }

    public void setOnMenuItemClickListener(Listener l) {
        listener = l;
    }

    public void show() {
        Collections.sort(items, (a, b) -> a.order - b.order);
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            PopupMenu popup = new PopupMenu(context, anchor);
            for (Item i : items) popup.getMenu().add(0, i.id, i.order, i.title);
            popup.setOnMenuItemClickListener(m -> {
                if (listener == null) return false;
                for (Item i : items) if (i.id == m.getItemId()) return listener.onMenuItemClick(i);
                return false;
            });
            popup.show();
            return;
        }
        CharSequence[] titles = new CharSequence[items.size()];
        for (int n = 0; n < titles.length; n++) titles[n] = items.get(n).title;
        new AlertDialog.Builder(context).setItems(titles, (d, which) -> {
            if (listener != null) listener.onMenuItemClick(items.get(which));
        }).show();
    }
}
