package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import com.piko.launcher.core.Prefs;

/** Which action buttons show on a contact result, and what tapping the row does. */
public final class ContactActionsActivity extends Activity {

    private static final String[] TAP_IDS = {Prefs.TAP_OPEN, Prefs.TAP_CALL, Prefs.TAP_COPY, Prefs.TAP_NONE};
    private static final String[] TAP_LABELS = {"Open contact", "Call", "Copy number", "Do nothing"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    private void build() {
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Contact actions");

        ui.section("Buttons");
        ui.hint("Choose which buttons appear on each contact result.");
        ui.toggle("Call", prefs.flag(Prefs.FLAG_ACTION_CALL), (b, c) -> prefs.setFlag(Prefs.FLAG_ACTION_CALL, c));
        ui.toggle("SMS", prefs.flag(Prefs.FLAG_ACTION_SMS), (b, c) -> prefs.setFlag(Prefs.FLAG_ACTION_SMS, c));
        ui.toggle("WhatsApp message", prefs.flag(Prefs.FLAG_ACTION_WA_MESSAGE), (b, c) -> prefs.setFlag(Prefs.FLAG_ACTION_WA_MESSAGE, c));
        ui.toggle("WhatsApp call", prefs.flag(Prefs.FLAG_ACTION_WA_CALL), (b, c) -> prefs.setFlag(Prefs.FLAG_ACTION_WA_CALL, c));

        ui.section("Tap on a result");
        String current = prefs.contactTapAction();
        String summary = TAP_LABELS[0];
        for (int i = 0; i < TAP_IDS.length; i++) if (TAP_IDS[i].equals(current)) summary = TAP_LABELS[i];
        ui.nav("Tap action", summary, v -> new AlertDialog.Builder(this)
                .setItems(TAP_LABELS, (d, which) -> {
                    prefs.setContactTapAction(TAP_IDS[which]);
                    build();
                })
                .show());
    }
}
