package com.piko.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.widget.Toast;

import com.piko.launcher.core.HomeItem;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Legacy "com.android.launcher.action.INSTALL_SHORTCUT" broadcast that older apps still send. */
public final class InstallShortcutReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            install(context, intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void install(Context context, Intent intent) throws Exception {
        Intent target = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (target == null) return;
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Bitmap icon = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        if (icon == null) {
            Parcelable res = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (res instanceof Intent.ShortcutIconResource) {
                Intent.ShortcutIconResource r = (Intent.ShortcutIconResource) res;
                try {
                    PackageManager pm = context.getPackageManager();
                    Resources apkRes = pm.getResourcesForApplication(r.packageName);
                    android.graphics.drawable.Drawable d = apkRes.getDrawable(apkRes.getIdentifier(r.resourceName, null, null), null);
                    icon = Bitmap.createBitmap(Math.max(1, d.getIntrinsicWidth()), Math.max(1, d.getIntrinsicHeight()), Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(icon);
                    d.setBounds(0, 0, c.getWidth(), c.getHeight());
                    d.draw(c);
                } catch (Exception ignored) {
                }
            }
        }
        String iconFile = "";
        if (icon != null) {
            iconFile = "shortcut_" + System.nanoTime() + ".png";
            try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), iconFile))) {
                icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }

        Prefs prefs = Prefs.get(context);
        HomeLayoutStore store = HomeLayoutStore.get(context);
        List<HomeItem> items = store.loadAll();
        int pages = Math.max(1, prefs.gridPageCount());
        for (int n = 0; n < pages; n++) {
            int p = (Math.min(prefs.defaultPage(), pages - 1) + n) % pages;
            for (int r = 0; r < prefs.rows(); r++) {
                for (int c = 0; c < prefs.columns(); c++) {
                    if (!PinShortcutActivity.cellFree(items, p, c, r)) continue;
                    HomeItem item = new HomeItem();
                    item.type = HomeItem.TYPE_SHORTCUT;
                    item.kind = "intent";
                    item.data = iconFile + "\n" + target.toUri(0);
                    item.label = name == null ? "Shortcut" : name;
                    item.page = p;
                    item.col = c;
                    item.row = r;
                    items.add(item);
                    store.saveAll(items);
                    prefs.bumpLayoutRevision();
                    Toast.makeText(context, item.label + " added", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        Toast.makeText(context, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
    }
}
