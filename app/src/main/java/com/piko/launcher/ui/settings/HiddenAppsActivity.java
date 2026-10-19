package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.AppCustomizationStore;
import com.piko.launcher.core.AppIndexer;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tick an app to hide it from search and the "add app" pickers; a filter box narrows the list as you type. */
public final class HiddenAppsActivity extends Activity {

    private final List<CheckBox> rows = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private TextView summary;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Hidden apps");
        ui.hint("Checked apps won't appear in search results. They still launch from the home screen.");

        EditText filter = new EditText(this);
        filter.setHint("Filter apps");
        filter.setSingleLine(true);
        filter.setTextColor(getResources().getColor(R.color.piko_text_primary));
        filter.setHintTextColor(getResources().getColor(R.color.piko_text_secondary));
        ui.root.addView(filter);

        summary = ui.text("", 12, R.color.piko_accent);
        summary.setPadding(0, ui.dp(8), 0, ui.dp(4));
        ui.root.addView(summary);

        List<AppInfo> apps = AppCustomizationStore.get(this).applyAll(AppIndexer.get(this).snapshot());
        Set<String> hidden = prefs.hiddenApps();
        for (AppInfo app : apps) {
            CheckBox cb = new CheckBox(this);
            cb.setText(app.label);
            cb.setTextColor(getResources().getColor(R.color.piko_text_primary));
            cb.setChecked(hidden.contains(app.key()));
            cb.setOnCheckedChangeListener((b, c) -> {
                prefs.setAppHidden(app.key(), c);
                updateSummary();
            });
            ui.root.addView(cb);
            rows.add(cb);
            labels.add(app.label.toLowerCase());
        }
        updateSummary();

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().trim().toLowerCase();
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).setVisibility(q.isEmpty() || labels.get(i).contains(q) ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void updateSummary() {
        int n = 0;
        for (CheckBox cb : rows) if (cb.isChecked()) n++;
        summary.setText(n + " hidden");
    }
}
