package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.WebSearchEngines;

import java.util.ArrayList;
import java.util.List;

/** Enable, reorder and add web search engines / AI chats shown on the search-the-web row. */
public final class SearchEnginesActivity extends Activity {

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        build();
    }

    private void build() {
        SettingsUi ui = new SettingsUi(this, "Web search & AI chats");
        ui.hint("Enabled engines appear left-to-right on the search-the-web row. Long-press a custom engine to delete it.");

        List<String> order = new ArrayList<>(prefs.searchEngineOrder());
        List<String> enabled = prefs.enabledSearchEngines();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            WebSearchEngines.Engine engine = prefs.engineById(id);
            View row = LayoutInflater.from(this).inflate(R.layout.item_settings_search_engine, ui.root, false);
            CheckBox checkBox = row.findViewById(R.id.checkbox);
            TextView label = row.findViewById(R.id.label);
            View up = row.findViewById(R.id.btn_up);
            View down = row.findViewById(R.id.btn_down);

            label.setText(prefs.isCustomEngine(id) ? engine.label + " (custom)" : engine.label);
            checkBox.setChecked(enabled.contains(id));
            checkBox.setOnCheckedChangeListener((b, checked) -> prefs.setSearchEngineEnabled(id, checked));
            if (prefs.isCustomEngine(id)) {
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete " + engine.label + "?")
                            .setPositiveButton(android.R.string.ok, (d, w) -> {
                                prefs.removeCustomEngine(id);
                                build();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return true;
                });
            }

            int index = i;
            up.setAlpha(index > 0 ? 1f : 0.3f);
            up.setOnClickListener(v -> move(order, index, index - 1));
            down.setAlpha(index < order.size() - 1 ? 1f : 0.3f);
            down.setOnClickListener(v -> move(order, index, index + 1));
            ui.root.addView(row);
        }

        ui.section("Custom");
        ui.nav("Add custom engine or AI chat", "Any site with a query URL", v -> showAddDialog());
    }

    private void move(List<String> order, int from, int to) {
        if (to < 0 || to >= order.size()) return;
        String moved = order.remove(from);
        order.add(to, moved);
        prefs.setSearchEngineOrder(order);
        build();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this);
        name.setHint("Name (e.g. Gemini)");
        EditText url = new EditText(this);
        url.setHint("URL, use %s for the query");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(url);
        new AlertDialog.Builder(this)
                .setTitle("Add engine")
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (n.isEmpty() || !u.startsWith("http")) {
                        Toast.makeText(this, "Enter a name and an http(s) URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.addCustomEngine(n, u);
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
