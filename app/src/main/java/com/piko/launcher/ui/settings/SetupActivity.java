package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

import com.piko.launcher.core.Prefs;

/** First-launch questions; every answer can be changed later in Settings. */
public final class SetupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Welcome to Piko");

        ui.hint("A few quick choices. You can change all of them later in Settings.");
        ui.section("Start");
        ui.toggle("Show the keyboard automatically", prefs.autoFocusKeyboard(),
                (b, on) -> prefs.setAutoFocusKeyboard(on));

        ui.section("Home screen");
        ui.toggle("Free-form icon placement", prefs.freeform(), (b, on) -> prefs.setFreeform(on));
        ui.toggle("Hide app names", prefs.hideAllLabels(), (b, on) -> prefs.setHideAllLabels(on));

        ui.section("Search");
        ui.toggle("Piko quick actions (settings, add page, ...)", prefs.flag(Prefs.FLAG_QUICK_ACTIONS),
                (b, on) -> prefs.setFlag(Prefs.FLAG_QUICK_ACTIONS, on));
        ui.toggle("Contacts", prefs.flag(Prefs.FLAG_SEARCH_CONTACTS),
                (b, on) -> prefs.setFlag(Prefs.FLAG_SEARCH_CONTACTS, on));

        ui.nav("Permissions", "Optional: contacts and calling",
                v -> startActivity(new android.content.Intent(this, PermissionsActivity.class)));

        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(v -> finish());
        ui.root.addView(done);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.get(this).markFirstRunDone();
        Prefs.get(this).bumpLayoutRevision();
    }
}
