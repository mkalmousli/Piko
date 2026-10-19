package com.piko.launcher.ui.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.piko.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Custom "add widget" picker: searchable, grouped by app (expandable), each
 * widget shown with a live preview. Replaces the stock system picker so we
 * control the whole bind flow ourselves (see MainActivity's widget methods).
 */
@android.annotation.TargetApi(16)
public final class WidgetPickerActivity extends Activity {

    public static final String EXTRA_PROVIDER = "extra_provider";
    /** Optional: show only the widgets of this package. */
    public static final String EXTRA_PACKAGE = "extra_package";
    /** Set on the result when the widget was long-pressed: the caller lets the user pick its position. */
    public static final String EXTRA_CHOOSE_PLACE = "extra_choose_place";

    private ExpandableListView list;
    private WidgetPickerAdapter adapter;
    private TextView emptyText;
    private List<WidgetProviderGroup> allGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_picker);
        com.piko.launcher.ui.settings.SettingsUi.installAppBar(this, getString(R.string.menu_add_widget));

        EditText searchInput = findViewById(R.id.widget_search_input);
        list = findViewById(R.id.widget_list);
        emptyText = findViewById(R.id.widget_empty_text);

        adapter = new WidgetPickerAdapter(this);
        list.setAdapter(adapter);
        list.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            AppWidgetProviderInfo info = adapter.getChild(groupPosition, childPosition);
            Intent result = new Intent();
            result.putExtra(EXTRA_PROVIDER, info.provider);
            setResult(RESULT_OK, result);
            finish();
            return true;
        });

        list.setOnItemLongClickListener((parent, v, position, id) -> {
            long packed = list.getExpandableListPosition(position);
            if (android.widget.ExpandableListView.getPackedPositionType(packed) != android.widget.ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                return false;
            }
            AppWidgetProviderInfo info = adapter.getChild(
                    android.widget.ExpandableListView.getPackedPositionGroup(packed),
                    android.widget.ExpandableListView.getPackedPositionChild(packed));
            Intent result = new Intent();
            result.putExtra(EXTRA_PROVIDER, info.provider);
            result.putExtra(EXTRA_CHOOSE_PLACE, true);
            setResult(RESULT_OK, result);
            finish();
            return true;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchInput.requestFocus();
        loadGroupsAsync();
    }

    private void loadGroupsAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<WidgetProviderGroup> groups = buildGroups();
            runOnUiThread(() -> {
                allGroups = groups;
                applyFilter("");
            });
        });
    }

    private List<WidgetProviderGroup> buildGroups() {
        PackageManager pm = getPackageManager();
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        Map<String, WidgetProviderGroup> byPackage = new LinkedHashMap<>();

        String only = getIntent().getStringExtra(EXTRA_PACKAGE);
        for (AppWidgetProviderInfo info : awm.getInstalledProviders()) {
            String packageName = info.provider.getPackageName();
            if (only != null && !only.equals(packageName)) continue;
            WidgetProviderGroup group = byPackage.get(packageName);
            if (group == null) {
                group = new WidgetProviderGroup(packageName, appLabel(pm, packageName), appIcon(pm, packageName));
                byPackage.put(packageName, group);
            }
            group.widgets.add(info);
        }

        List<WidgetProviderGroup> groups = new ArrayList<>(byPackage.values());
        Collections.sort(groups, (a, b) -> a.appLabel.toLowerCase().compareTo(b.appLabel.toLowerCase()));
        for (WidgetProviderGroup group : groups) {
            Collections.sort(group.widgets, (a, b) ->
                    adapter.label(a).toLowerCase().compareTo(adapter.label(b).toLowerCase()));
        }
        return groups;
    }

    private String appLabel(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private Drawable appIcon(PackageManager pm, String packageName) {
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return com.piko.launcher.util.Compat.drawable(getResources(), R.drawable.ic_placeholder_app);
        }
    }

    private void applyFilter(String rawQuery) {
        String query = rawQuery.trim().toLowerCase();
        boolean searching = !com.piko.launcher.util.Compat.isEmpty(query);

        List<WidgetProviderGroup> filtered = new ArrayList<>();
        for (WidgetProviderGroup group : allGroups) {
            if (!searching) {
                filtered.add(group);
                continue;
            }
            boolean groupMatches = group.appLabel.toLowerCase().contains(query);
            if (groupMatches) {
                filtered.add(group);
                continue;
            }
            WidgetProviderGroup partial = new WidgetProviderGroup(group.packageName, group.appLabel, group.appIcon);
            for (AppWidgetProviderInfo info : group.widgets) {
                if (adapter.label(info).toLowerCase().contains(query)) {
                    partial.widgets.add(info);
                }
            }
            if (!com.piko.launcher.util.Compat.isEmpty(partial.widgets)) filtered.add(partial);
        }

        adapter.submit(filtered);
        emptyText.setVisibility(com.piko.launcher.util.Compat.isEmpty(filtered) ? android.view.View.VISIBLE : android.view.View.GONE);
        list.setVisibility(com.piko.launcher.util.Compat.isEmpty(filtered) ? android.view.View.GONE : android.view.View.VISIBLE);

        if (searching || getIntent().getStringExtra(EXTRA_PACKAGE) != null) {
            for (int i = 0; i < filtered.size(); i++) list.expandGroup(i);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adapter.shutdown();
    }
}
