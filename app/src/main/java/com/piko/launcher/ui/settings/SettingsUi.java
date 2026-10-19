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
public final class SettingsUi {

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
        root.setPadding(pad, dp(8), pad, dp(32));
        scroll.addView(root);
        activity.setContentView(scroll);
        installAppBar(activity, title);
    }

    /** Wraps the activity's current content under a top bar with a back arrow and the screen title. */
    public static void installAppBar(Activity activity, String title) {
        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        if (content.getChildCount() == 0) return;
        View body = content.getChildAt(0);
        content.removeView(body);
        float d = activity.getResources().getDisplayMetrics().density;

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(activity.getResources().getColor(R.color.piko_bg));

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (24 * d), (int) (16 * d), 0);
        bar.setElevation(4 * d);
        bar.setBackgroundColor(activity.getResources().getColor(R.color.piko_bg));

        android.widget.ImageView back = new android.widget.ImageView(activity);
        back.setImageResource(R.drawable.ic_back);
        back.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        back.setBackgroundResource(R.drawable.bg_icon_button);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> activity.onBackPressed());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d));
        backLp.setMargins((int) (8 * d), 0, (int) (8 * d), 0);
        bar.addView(back, backLp);

        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextSize(20);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(activity.getResources().getColor(R.color.piko_text_primary));
        t.setSingleLine(true);
        bar.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        column.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(column, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
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

    interface IntCb {
        void on(int value);
    }

    /** Label with a − value + stepper on the right. */
    void stepper(String label, int initial, int min, int max, IntCb cb) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(label, 15, R.color.piko_text_primary),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final int[] v = {initial};
        TextView value = text(String.valueOf(initial), 16, R.color.piko_text_primary);
        value.setGravity(Gravity.CENTER);
        android.widget.ImageButton minus = stepButton(R.drawable.ic_minus);
        android.widget.ImageButton plus = stepButton(R.drawable.ic_add);
        minus.setOnClickListener(x -> {
            if (v[0] > min) { v[0]--; value.setText(String.valueOf(v[0])); cb.on(v[0]); }
        });
        plus.setOnClickListener(x -> {
            if (v[0] < max) { v[0]++; value.setText(String.valueOf(v[0])); cb.on(v[0]); }
        });
        row.addView(minus);
        row.addView(value, new LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(plus);
        root.addView(row);
    }

    private android.widget.ImageButton stepButton(int icon) {
        android.widget.ImageButton b = new android.widget.ImageButton(activity);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.bg_pressable);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return b;
    }

    /** A tappable danger-coloured text action. */
    void action(String label, int colorRes, View.OnClickListener l) {
        TextView t = text(label, 15, colorRes);
        t.setBackgroundResource(R.drawable.bg_pressable);
        t.setPadding(dp(4), dp(12), dp(4), dp(12));
        t.setOnClickListener(l);
        root.addView(t);
    }
}
