package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.IconPacks;
import com.piko.launcher.core.Prefs;

import java.util.List;

/** Pick an installed ADW/Nova-style icon pack, or go back to the system icons. */
public final class IconPackActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Icon pack");
        List<IconPacks.Pack> packs = IconPacks.get(this).installed();
        ui.hint(com.piko.launcher.util.Compat.isEmpty(packs)
                ? "No icon packs found. Install one from your app store (ADW, Nova and Apex packs work)."
                : "Applies to the home screen and search results.");

        RadioGroup group = new RadioGroup(this);
        String current = prefs.iconPack();
        addOption(group, "System icons", "", current);
        for (IconPacks.Pack p : packs) addOption(group, p.label, p.packageName, current);
        ui.root.addView(group);

        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton rb = g.findViewById(id);
            prefs.setIconPack((String) rb.getTag());
            IconCache.get(this).evict(null);
            prefs.bumpLayoutRevision();
        });
    }

    private void addOption(RadioGroup group, String label, String pkg, String current) {
        RadioButton rb = new RadioButton(this);
        rb.setId(com.piko.launcher.util.Compat.generateViewId());
        rb.setText(label);
        rb.setTag(pkg);
        rb.setTextColor(getResources().getColor(com.piko.launcher.R.color.piko_text_primary));
        rb.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0,
                (int) (10 * getResources().getDisplayMetrics().density));
        group.addView(rb);
        if (pkg.equals(current)) group.check(rb.getId());
    }
}
