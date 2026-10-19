package com.piko.launcher.ui.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.ContactIndexer;
import com.piko.launcher.util.PermissionUtil;

/** Explains each permission Piko can use, shows whether it is granted and lets the user grant or revoke it. */
public final class PermissionsActivity extends Activity {

    private static final int REQ = 601;
    private SettingsUi ui;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new SettingsUi(this, "Permissions");
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ui != null) render();
    }

    private void render() {
        // Keep the first child area stable: rebuild everything below the app bar's body.
        ui.root.removeAllViews();
        ui.hint("Piko works without any of these. Each one only unlocks the feature listed under it.");
        row("Contacts", Manifest.permission.READ_CONTACTS,
                "Search your contacts by name or number and call or message them from the results.");
        row("Phone calls", Manifest.permission.CALL_PHONE,
                "Call straight away from a result instead of opening the dialer (needs “direct call” on).");
        TextView settings = ui.text("Open system app settings  ›", 15, R.color.piko_accent);
        settings.setPadding(0, ui.dp(24), 0, ui.dp(12));
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null))));
        ui.root.addView(settings);
    }

    private void row(String title, String permission, String why) {
        boolean granted = PermissionUtil.has(this, permission);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, ui.dp(16), 0, ui.dp(8));
        row.addView(ui.text(title + (granted ? ": allowed" : ": not allowed"), 16, R.color.piko_text_primary));
        row.addView(ui.text(why, 12, R.color.piko_text_secondary));
        TextView action = ui.text(granted ? "Manage in system settings" : "Allow", 14, R.color.piko_accent);
        action.setPadding(0, ui.dp(8), 0, ui.dp(8));
        action.setOnClickListener((View v) -> {
            if (granted) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } else {
                requestPermissions(new String[]{permission}, REQ);
            }
        });
        row.addView(action);
        ui.root.addView(row);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (PermissionUtil.has(this, Manifest.permission.READ_CONTACTS)) ContactIndexer.get(this).loadAsync();
        render();
    }
}
