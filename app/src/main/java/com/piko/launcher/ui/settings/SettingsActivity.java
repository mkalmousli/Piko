package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.ShortcutContact;
import com.piko.launcher.core.ShortcutStore;
import com.piko.launcher.util.PermissionUtil;

import java.util.List;

/** Grid geometry, default page, call behaviour and the quick-contact dock — all in one flat screen. */
public final class SettingsActivity extends Activity {

    private static final int MIN_COLUMNS = 3, MAX_COLUMNS = 8;
    private static final int MIN_ROWS = 3, MAX_ROWS = 10;
    private static final int MIN_PAGES = 1, MAX_PAGES = 5;
    private static final int REQ_PICK_CONTACT = 901;

    private Prefs prefs;
    private ShortcutStore shortcutStore;

    private TextView textColumns, textRows;
    private LinearLayout shortcutsContainer;
    private TextView textNoShortcuts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        shortcutStore = ShortcutStore.get(this);
        setContentView(R.layout.activity_settings);

        textColumns = findViewById(R.id.text_columns);
        textRows = findViewById(R.id.text_rows);
        shortcutsContainer = findViewById(R.id.shortcuts_container);
        textNoShortcuts = findViewById(R.id.text_no_shortcuts);

        setupStepper(R.id.btn_columns_minus, R.id.btn_columns_plus, textColumns,
                prefs.columns(), MIN_COLUMNS, MAX_COLUMNS, value -> {
                    prefs.setColumns(value);
                    prefs.bumpLayoutRevision();
                });
        setupStepper(R.id.btn_rows_minus, R.id.btn_rows_plus, textRows,
                prefs.rows(), MIN_ROWS, MAX_ROWS, value -> {
                    prefs.setRows(value);
                    prefs.bumpLayoutRevision();
                });
        setupStepper(R.id.btn_pages_minus, R.id.btn_pages_plus, findViewById(R.id.text_pages),
                prefs.gridPageCount(), MIN_PAGES, MAX_PAGES, value -> {
                    prefs.setGridPageCount(value);
                    prefs.bumpLayoutRevision();
                });

        Switch defaultPageSwitch = findViewById(R.id.switch_default_page);
        defaultPageSwitch.setChecked(prefs.defaultPage() == Prefs.DEFAULT_PAGE_HOME);
        defaultPageSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) ->
                prefs.setDefaultPage(checked ? Prefs.DEFAULT_PAGE_HOME : Prefs.DEFAULT_PAGE_SEARCH));

        Switch directCallSwitch = findViewById(R.id.switch_direct_call);
        directCallSwitch.setChecked(prefs.directCall());
        directCallSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) ->
                prefs.setDirectCall(checked));

        TextView searchEngineText = findViewById(R.id.text_search_engine);
        refreshSearchEngineLabel(searchEngineText);
        searchEngineText.setOnClickListener(v -> {
            boolean nowBrave = !Prefs.SEARCH_ENGINE_BRAVE.equals(prefs.webSearchEngine());
            prefs.setWebSearchEngine(nowBrave ? Prefs.SEARCH_ENGINE_BRAVE : Prefs.SEARCH_ENGINE_GOOGLE);
            refreshSearchEngineLabel(searchEngineText);
        });

        findViewById(R.id.btn_reset_layout).setOnClickListener(v -> confirmResetLayout());
        findViewById(R.id.btn_add_shortcut).setOnClickListener(v -> startAddShortcut());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshShortcutsUi();
    }

    private void refreshSearchEngineLabel(TextView label) {
        boolean brave = Prefs.SEARCH_ENGINE_BRAVE.equals(prefs.webSearchEngine());
        label.setText(brave ? "Brave" : "Google");
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private void setupStepper(int minusId, int plusId, TextView valueText, int initial, int min, int max, IntConsumer onChange) {
        final int[] value = {initial};
        valueText.setText(String.valueOf(value[0]));
        ImageButton minus = findViewById(minusId);
        ImageButton plus = findViewById(plusId);
        minus.setOnClickListener(v -> {
            if (value[0] > min) {
                value[0]--;
                valueText.setText(String.valueOf(value[0]));
                onChange.accept(value[0]);
            }
        });
        plus.setOnClickListener(v -> {
            if (value[0] < max) {
                value[0]++;
                valueText.setText(String.valueOf(value[0]));
                onChange.accept(value[0]);
            }
        });
    }

    private void confirmResetLayout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_reset_layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    HomeLayoutStore.get(this).clear();
                    prefs.bumpLayoutRevision();
                    Toast.makeText(this, R.string.settings_reset_layout, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ shortcuts

    private void startAddShortcut() {
        if (!PermissionUtil.has(this, android.Manifest.permission.READ_CONTACTS)) {
            PermissionUtil.requestContacts(this);
            return;
        }
        Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(pick, REQ_PICK_CONTACT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtil.REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startAddShortcut();
            } else {
                Toast.makeText(this, R.string.permission_contacts_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_CONTACT || resultCode != RESULT_OK || data == null) return;

        Uri contactData = data.getData();
        if (contactData == null) return;

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        };
        try (Cursor cursor = getContentResolver().query(contactData, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]));
                String number = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
                long contactId = cursor.getLong(cursor.getColumnIndexOrThrow(projection[2]));

                Intent intent = new Intent(this, ShortcutPickerActivity.class);
                intent.putExtra(ShortcutPickerActivity.EXTRA_NAME, name);
                intent.putExtra(ShortcutPickerActivity.EXTRA_NUMBER, number);
                intent.putExtra(ShortcutPickerActivity.EXTRA_ID, contactId);
                startActivity(intent);
            }
        }
    }

    private void refreshShortcutsUi() {
        shortcutsContainer.removeAllViews();
        List<ShortcutContact> shortcuts = shortcutStore.loadAll();
        textNoShortcuts.setVisibility(shortcuts.isEmpty() ? View.VISIBLE : View.GONE);

        for (ShortcutContact contact : shortcuts) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_settings_shortcut, shortcutsContainer, false);
            TextView avatar = row.findViewById(R.id.avatar);
            TextView name = row.findViewById(R.id.name);
            TextView actionsSummary = row.findViewById(R.id.actions_summary);
            ImageButton remove = row.findViewById(R.id.btn_remove);

            name.setText(contact.name);
            avatar.setText(contact.name != null && !contact.name.isEmpty()
                    ? contact.name.substring(0, 1).toUpperCase() : "?");
            actionsSummary.setText(describeActions(contact));
            remove.setOnClickListener(v -> {
                shortcutStore.remove(contact.id);
                refreshShortcutsUi();
            });

            shortcutsContainer.addView(row);
        }
    }

    private String describeActions(ShortcutContact contact) {
        StringBuilder sb = new StringBuilder();
        if (contact.has(ShortcutContact.ACTION_CALL)) sb.append(getString(R.string.action_call)).append(" · ");
        if (contact.has(ShortcutContact.ACTION_SMS)) sb.append(getString(R.string.action_message)).append(" · ");
        if (contact.has(ShortcutContact.ACTION_WA_MESSAGE)) sb.append(getString(R.string.action_whatsapp_message)).append(" · ");
        if (contact.has(ShortcutContact.ACTION_WA_CALL)) sb.append(getString(R.string.action_whatsapp_call)).append(" · ");
        if (sb.length() >= 3) sb.setLength(sb.length() - 3);
        return sb.toString();
    }
}
