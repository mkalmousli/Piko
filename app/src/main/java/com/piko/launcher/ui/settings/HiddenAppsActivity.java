package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.piko.launcher.R;
import com.piko.launcher.core.AppCustomizationStore;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.AppIndexer;
import com.piko.launcher.core.Prefs;

import java.util.List;

/** Tick an app to hide it from search and the "add app" pickers. */
public final class HiddenAppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Hidden apps");
        ui.hint("Checked apps won't appear in search results. They still launch from the home screen.");
        ui.root.addView(new LinearLayout(this));

        List<AppInfo> apps = AppCustomizationStore.get(this).applyAll(AppIndexer.get(this).snapshot());
        java.util.Set<String> hidden = prefs.hiddenApps();
        for (AppInfo app : apps) {
            CheckBox cb = new CheckBox(this);
            cb.setText(app.label);
            cb.setTextColor(getResources().getColor(R.color.piko_text_primary));
            cb.setChecked(hidden.contains(app.key()));
            cb.setOnCheckedChangeListener((b, c) -> prefs.setAppHidden(app.key(), c));
            ui.root.addView(cb);
        }
    }
}
