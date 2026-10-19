package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.piko.launcher.R;

/** Tiny programmatic builder so the drill-down settings screens share one look without a layout file each. */
final class SettingsUi {

    final Activity activity;
    final LinearLayout root;
    final ScrollView scroll;

    SettingsUi(Activity activity, String title) {
        this.activity = activity;
        scroll = new ScrollView(activity);
        scroll.setBackgroundColor(activity.getResources().getColor(R.color.piko_bg));
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(36), pad, dp(32));
        scroll.addView(root);

        TextView t = text(title, 24, R.color.piko_text_primary);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);
        activity.setContentView(scroll);
    }

    int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density);
    }

    TextView text(String s, int sp, int colorRes) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(activity.getResources().getColor(colorRes));
        return t;
    }

    void section(String label) {
        TextView t = text(label, 13, R.color.piko_accent);
        t.setPadding(0, dp(28), 0, dp(4));
        root.addView(t);
    }

    void hint(String s) {
        TextView t = text(s, 12, R.color.piko_text_secondary);
        t.setPadding(0, dp(2), 0, dp(4));
        root.addView(t);
    }

    Switch toggle(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView t = text(label, 15, R.color.piko_text_primary);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(activity);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        root.addView(row);
        return sw;
    }

    /** A tappable row that drills into another screen; {@code summary} may be null. */
    void nav(String label, String summary, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_pressable);
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.addView(text(label + "  ›", 15, R.color.piko_text_primary));
        if (summary != null) row.addView(text(summary, 12, R.color.piko_text_secondary));
        row.setOnClickListener(l);
        root.addView(row);
    }
}
