package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.AppCustomizationStore;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Rename an app, add extra search tags, or swap its icon for a custom image. */
public final class AppEditActivity extends Activity {

    private static final String EXTRA_PKG = "pkg";
    private static final String EXTRA_ACT = "act";
    private static final String EXTRA_LABEL = "label";
    private static final int REQ_PICK_ICON = 4201;

    public static void start(Context context, AppInfo app) {
        Intent intent = new Intent(context, AppEditActivity.class);
        intent.putExtra(EXTRA_PKG, app.packageName);
        intent.putExtra(EXTRA_ACT, app.activityName);
        intent.putExtra(EXTRA_LABEL, app.label);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private AppCustomizationStore store;
    private String pkg, act, key, rawLabel;
    private String pendingIconPath;
    private ImageView iconPreview;
    private EditText nameInput, tagsInput;
    private android.widget.CompoundButton hideLabelSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = AppCustomizationStore.get(this);
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        act = getIntent().getStringExtra(EXTRA_ACT);
        rawLabel = getIntent().getStringExtra(EXTRA_LABEL);
        key = pkg + "/" + act;

        AppCustomizationStore.Entry existing = store.get(key);
        pendingIconPath = existing != null ? existing.iconPath : null;

        setContentView(buildView(existing));
        SettingsUi.installAppBar(this, getString(R.string.menu_edit_app));
    }

    private View buildView(AppCustomizationStore.Entry existing) {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        iconPreview = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = dp(12);
        iconPreview.setLayoutParams(iconParams);
        String iconLabel = existing != null && existing.label != null ? existing.label : rawLabel;
        IconCache.get(this).load(new AppInfo(pkg, act, iconLabel, "", pendingIconPath), iconPreview, dp(72));
        root.addView(iconPreview);

        Button changeIconBtn = new Button(this);
        changeIconBtn.setText(R.string.change_icon);
        changeIconBtn.setOnClickListener(v -> pickIcon());
        root.addView(changeIconBtn);

        TextView nameLabel = new TextView(this);
        nameLabel.setText(R.string.app_name_label);
        nameLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(nameLabel);

        nameInput = new EditText(this);
        nameInput.setText(existing != null && existing.label != null ? existing.label : rawLabel);
        root.addView(nameInput);

        TextView tagsLabel = new TextView(this);
        tagsLabel.setText(R.string.app_tags_label);
        tagsLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(tagsLabel);

        tagsInput = new EditText(this);
        tagsInput.setHint(R.string.app_tags_hint);
        if (existing != null) tagsInput.setText(existing.tags);
        root.addView(tagsInput);

        hideLabelSwitch = com.piko.launcher.util.Compat.newSwitch(this);
        hideLabelSwitch.setText(R.string.hide_name_on_home);
        hideLabelSwitch.setTextColor(getResources().getColor(R.color.piko_text_primary));
        hideLabelSwitch.setPadding(0, dp(16), 0, 0);
        hideLabelSwitch.setChecked(existing != null && existing.hideLabel);
        root.addView(hideLabelSwitch);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(24), 0, 0);

        Button resetBtn = new Button(this);
        resetBtn.setText(R.string.action_reset);
        resetBtn.setOnClickListener(v -> {
            store.clear(key);
            Prefs.get(this).bumpLayoutRevision();
            Toast.makeText(this, R.string.action_reset, Toast.LENGTH_SHORT).show();
            finish();
        });
        buttonRow.addView(resetBtn);

        Button saveBtn = new Button(this);
        saveBtn.setText(R.string.action_save);
        saveBtn.setOnClickListener(v -> save());
        buttonRow.addView(saveBtn);

        root.addView(buttonRow);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void pickIcon() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.change_icon)
                .setItems(new String[]{"Icons from your apps", "Icon pack", "Image from device"}, (d, which) -> {
                    if (which == 0) pickFromApps();
                    else if (which == 1) pickFromPack();
                    else pickImage();
                })
                .show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK_ICON);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFromApps() {
        android.content.pm.PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        java.util.Collections.sort(apps, (x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (android.content.pm.ResolveInfo ri : apps) labels.add(String.valueOf(ri.loadLabel(pm)));
        showIconGrid(labels, i -> apps.get(i).loadIcon(pm));
    }

    private void pickFromPack() {
        com.piko.launcher.core.IconPacks packs = com.piko.launcher.core.IconPacks.get(this);
        java.util.List<String> names = packs.drawableNames();
        if (com.piko.launcher.util.Compat.isEmpty(names)) {
            Toast.makeText(this, "Choose an icon pack in Settings first", Toast.LENGTH_LONG).show();
            return;
        }
        showIconGrid(names, i -> packs.drawableByName(names.get(i)));
    }

    private interface DrawableAt {
        android.graphics.drawable.Drawable get(int index);
    }

    /** Searchable grid of icons; tapping one renders it to a PNG and uses it as this app's icon. */
    private void showIconGrid(java.util.List<String> labels, DrawableAt source) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), 0);
        EditText filter = new EditText(this);
        filter.setHint(R.string.search_hint);
        filter.setSingleLine(true);
        box.addView(filter);
        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(8));
        box.addView(grid, new LinearLayout.LayoutParams(-1, dp(360)));

        final java.util.List<Integer> shown = new java.util.ArrayList<>();
        final AppEditActivity self = this;
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, android.view.ViewGroup parent) {
                ImageView iv = v instanceof ImageView ? (ImageView) v : new ImageView(self);
                iv.setLayoutParams(new android.widget.AbsListView.LayoutParams(dp(48), dp(48)));
                iv.setPadding(dp(4), dp(4), dp(4), dp(4));
                iv.setImageDrawable(source.get(shown.get(p)));
                return iv;
            }
        };
        Runnable refresh = () -> {
            String q = filter.getText().toString().trim().toLowerCase(java.util.Locale.ENGLISH);
            shown.clear();
            for (int i = 0; i < labels.size(); i++) {
                if (com.piko.launcher.util.Compat.isEmpty(q) || labels.get(i).toLowerCase(java.util.Locale.ENGLISH).contains(q)) shown.add(i);
            }
            adapter.notifyDataSetChanged();
        };
        refresh.run();
        grid.setAdapter(adapter);
        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { refresh.run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(box).create();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            android.graphics.drawable.Drawable d = source.get(shown.get(position));
            if (d != null) applyPickedDrawable(d);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void applyPickedDrawable(android.graphics.drawable.Drawable d) {
        int size = dp(96);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        String path = saveBitmap(bmp);
        if (path == null) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingIconPath = path;
        IconCache.get(this).evict(null);
        IconCache.get(this).load(new AppInfo(pkg, act, nameInput.getText().toString(), "", pendingIconPath), iconPreview, dp(72));
    }

    private String saveBitmap(Bitmap bitmap) {
        try {
            File dir = new File(getFilesDir(), "custom_icons");
            if (!dir.exists()) dir.mkdirs();
            // A fresh name per pick so the icon cache can't serve the previous image.
            File outFile = new File(dir, key.replace("/", "_") + "_" + System.currentTimeMillis() + ".png");
            try (OutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_ICON || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String path = copyIconToInternalStorage(data.getData());
        if (path != null) {
            pendingIconPath = path;
            IconCache.get(this).evict(null);
            IconCache.get(this).load(new AppInfo(pkg, act, nameInput.getText().toString(), "", pendingIconPath), iconPreview, dp(72));
        } else {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String copyIconToInternalStorage(Uri source) {
        try {
            ContentResolver resolver = getContentResolver();
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(source)) {
                bitmap = BitmapFactory.decodeStream(in);
            }
            if (bitmap == null) return null;

            File dir = new File(getFilesDir(), "custom_icons");
            if (!dir.exists()) dir.mkdirs();
            File outFile = new File(dir, key.replace("/", "_") + ".png");
            try (OutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        String tags = tagsInput.getText().toString().trim();
        store.save(key, com.piko.launcher.util.Compat.isEmpty(name) ? null : name, tags, pendingIconPath, hideLabelSwitch.isChecked());
        Prefs.get(this).bumpLayoutRevision();
        IconCache.get(this).evict(null);
        finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
