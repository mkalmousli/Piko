package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.piko.launcher.core.Prefs;

/** Hub for everything that shapes search: sources, hidden apps, contact actions and web engines. */
public final class SearchSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        Prefs prefs = Prefs.get(this);
        SettingsUi ui = new SettingsUi(this, "Search");

        ui.section("Sources");
        ui.hint("Turn off anything you don't want in the results.");
        ui.toggle("Apps", prefs.flag(Prefs.FLAG_SEARCH_APPS), (b, c) -> prefs.setFlag(Prefs.FLAG_SEARCH_APPS, c));
        ui.toggle("Contacts", prefs.flag(Prefs.FLAG_SEARCH_CONTACTS), (b, c) -> prefs.setFlag(Prefs.FLAG_SEARCH_CONTACTS, c));
        ui.toggle("Calculator", prefs.flag(Prefs.FLAG_SEARCH_CALC), (b, c) -> prefs.setFlag(Prefs.FLAG_SEARCH_CALC, c));
        ui.toggle("Currency conversion", prefs.flag(Prefs.FLAG_SEARCH_CURRENCY), (b, c) -> prefs.setFlag(Prefs.FLAG_SEARCH_CURRENCY, c));
        ui.toggle("Search-the-web row", prefs.flag(Prefs.FLAG_SEARCH_WEB), (b, c) -> prefs.setFlag(Prefs.FLAG_SEARCH_WEB, c));

        ui.section("Configure");
        int hidden = prefs.hiddenApps().size();
        ui.nav("Hidden apps", hidden == 0 ? "None hidden" : hidden + " hidden from search",
                v -> startActivity(new Intent(this, HiddenAppsActivity.class)));
        ui.nav("Contact actions", "Buttons and tap behaviour on contact results",
                v -> startActivity(new Intent(this, ContactActionsActivity.class)));
        ui.nav("Web search & AI chats", prefs.enabledSearchEngines().size() + " enabled",
                v -> startActivity(new Intent(this, SearchEnginesActivity.class)));
    }
}
