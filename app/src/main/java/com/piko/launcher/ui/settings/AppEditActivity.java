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
    private android.widget.Switch hideLabelSwitch;

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

        hideLabelSwitch = new android.widget.Switch(this);
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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK_ICON);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show();
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
        store.save(key, name.isEmpty() ? null : name, tags, pendingIconPath, hideLabelSwitch.isChecked());
        Prefs.get(this).bumpLayoutRevision();
        IconCache.get(this).evict(null);
        finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
