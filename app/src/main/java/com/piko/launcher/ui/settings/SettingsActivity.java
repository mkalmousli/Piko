package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;

/** Grid geometry, default page, behaviour and links to the detail screens. */
public final class SettingsActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, getString(R.string.settings_title));

        ui.section("Home");
        ui.stepper("Grid columns", prefs.columns(), 3, 7, v -> {
            prefs.setColumns(v);
            prefs.bumpLayoutRevision();
        });
        ui.stepper("Grid rows", prefs.rows(), 3, 9, v -> {
            prefs.setRows(v);
            prefs.bumpLayoutRevision();
        });
        ui.nav("Default page", pageName(prefs.defaultPage()), v -> pickDefaultPage(prefs));
        ui.toggle("Free-form icon placement", prefs.freeform(), (b, on) -> {
            prefs.setFreeform(on);
            prefs.bumpLayoutRevision();
        });
        ui.toggle("Hide all app names", prefs.hideAllLabels(), (b, on) -> {
            prefs.setHideAllLabels(on);
            prefs.bumpLayoutRevision();
        });
        ui.nav("Icon pack", null, v -> startActivity(new Intent(this, IconPackActivity.class)));

        ui.section("Behaviour");
        ui.toggle("Show keyboard on the search page", prefs.autoFocusKeyboard(),
                (b, on) -> prefs.setAutoFocusKeyboard(on));
        ui.toggle("Call directly (skip dialer)", prefs.directCall(), (b, on) -> prefs.setDirectCall(on));
        ui.nav("Permissions", null, v -> startActivity(new Intent(this, PermissionsActivity.class)));

        ui.section("Search");
        ui.nav("Search settings", null, v -> startActivity(new Intent(this, SearchSettingsActivity.class)));

        ui.section("Setup");
        ui.nav("Run first-time setup again", null, v -> startActivity(new Intent(this, SetupActivity.class)));
        ui.action("Reset home layout", R.color.piko_danger, v -> confirmReset(prefs));
    }

    private String pageName(int index) {
        return index == 0 ? "Search page" : "Home page " + index;
    }

    private void pickDefaultPage(Prefs prefs) {
        int n = Math.max(1, prefs.gridPageCount());
        String[] names = new String[n + 1];
        for (int i = 0; i <= n; i++) names[i] = pageName(i);
        new AlertDialog.Builder(this)
                .setTitle("Default page")
                .setSingleChoiceItems(names, Math.min(prefs.defaultPage(), n), (d, which) -> {
                    prefs.setDefaultPage(which);
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void confirmReset(Prefs prefs) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    HomeLayoutStore.get(this).clear();
                    prefs.bumpLayoutRevision();
                    Toast.makeText(this, R.string.settings_reset_layout, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
