package com.piko.launcher;

import android.app.Activity;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.piko.launcher.core.HomeItem;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;

import java.util.List;

/** Handles "add to home screen" requests other apps send through ShortcutManager.requestPinShortcut. */
public final class PinShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handle();
        } catch (Exception e) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void handle() {
        LauncherApps apps = getSystemService(LauncherApps.class);
        LauncherApps.PinItemRequest request = apps == null ? null : apps.getPinItemRequest(getIntent());
        if (request == null || request.getRequestType() != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return;
        ShortcutInfo info = request.getShortcutInfo();
        if (info == null) return;

        Prefs prefs = Prefs.get(this);
        HomeLayoutStore store = HomeLayoutStore.get(this);
        List<HomeItem> items = store.loadAll();
        int cols = prefs.columns(), rows = prefs.rows(), pages = Math.max(1, prefs.gridPageCount());
        int page = -1, col = 0, row = 0;
        search:
        for (int p = 0; p < pages; p++) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (cellFree(items, p, c, r)) {
                        page = p;
                        col = c;
                        row = r;
                        break search;
                    }
                }
            }
        }
        if (page < 0 || !request.accept()) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        HomeItem item = new HomeItem();
        item.type = HomeItem.TYPE_SHORTCUT;
        item.kind = "pin";
        item.data = info.getPackage() + "\n" + info.getId();
        CharSequence label = info.getShortLabel();
        item.label = label == null ? info.getPackage() : label.toString();
        item.page = page;
        item.col = col;
        item.row = row;
        items.add(item);
        store.saveAll(items);
        prefs.bumpLayoutRevision();
        Toast.makeText(this, item.label + " added", Toast.LENGTH_SHORT).show();
    }

    private static boolean cellFree(List<HomeItem> items, int page, int col, int row) {
        for (HomeItem it : items) {
            if (it.page != page) continue;
            if (col >= it.col && col < it.col + it.colSpan && row >= it.row && row < it.row + it.rowSpan) return false;
        }
        return true;
    }
}
