package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.ShortcutContact;
import com.piko.launcher.core.ShortcutStore;

/** Second step of adding a quick contact: pick which actions the dock chip should offer. */
public final class ShortcutPickerActivity extends Activity {

    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_NUMBER = "extra_number";
    public static final String EXTRA_ID = "extra_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut_picker);
        SettingsUi.installAppBar(this, getString(R.string.menu_add_app));

        String name = getIntent().getStringExtra(EXTRA_NAME);
        String number = getIntent().getStringExtra(EXTRA_NUMBER);
        long id = getIntent().getLongExtra(EXTRA_ID, System.currentTimeMillis());

        ((TextView) findViewById(R.id.contact_name)).setText(name);
        ((TextView) findViewById(R.id.contact_number)).setText(number);

        CheckBox checkCall = findViewById(R.id.check_call);
        CheckBox checkSms = findViewById(R.id.check_sms);
        CheckBox checkMessage = findViewById(R.id.check_wa_message);
        CheckBox checkCallWa = findViewById(R.id.check_wa_call);

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            int actions = 0;
            if (checkCall.isChecked()) actions |= ShortcutContact.ACTION_CALL;
            if (checkSms.isChecked()) actions |= ShortcutContact.ACTION_SMS;
            if (checkMessage.isChecked()) actions |= ShortcutContact.ACTION_WA_MESSAGE;
            if (checkCallWa.isChecked()) actions |= ShortcutContact.ACTION_WA_CALL;

            if (actions == 0) {
                checkCall.setChecked(true);
                actions = ShortcutContact.ACTION_CALL;
            }

            ShortcutContact contact = new ShortcutContact(id, name, number, null, actions);
            ShortcutStore.get(this).add(contact);
            finish();
        });
    }
}
