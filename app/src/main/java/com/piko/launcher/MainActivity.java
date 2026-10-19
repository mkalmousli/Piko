package com.piko.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.piko.launcher.core.AppIndexer;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.ContactIndexer;
import com.piko.launcher.core.ContactInfo;
import com.piko.launcher.core.HomeItem;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.SearchEngine;
import com.piko.launcher.core.SearchLearningStore;
import com.piko.launcher.core.SearchResult;
import com.piko.launcher.core.ShortcutContact;
import com.piko.launcher.core.ShortcutStore;
import com.piko.launcher.core.UsageStore;
import com.piko.launcher.ui.AppListAdapter;
import com.piko.launcher.ui.HorizontalPager;
import com.piko.launcher.ui.SearchResultAdapter;
import com.piko.launcher.ui.home.HomeGridLayout;
import com.piko.launcher.ui.home.HomeIconViewFactory;
import com.piko.launcher.ui.settings.SettingsActivity;
import com.piko.launcher.ui.settings.ShortcutPickerActivity;
import com.piko.launcher.ui.widget.WidgetHostController;
import com.piko.launcher.ui.widget.WidgetPickerActivity;
import com.piko.launcher.util.IntentUtil;
import com.piko.launcher.util.PermissionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Piko's home screen: a search-first page (index 0, focused with the
 * keyboard up by default) followed by N configurable icon/widget grid pages,
 * all swipeable through {@link HorizontalPager}.
 */
public final class MainActivity extends Activity implements AppIndexer.Listener, ContactIndexer.Listener {

    private static final int REQ_PICK_WIDGET_PROVIDER = 771;
    private static final int REQ_BIND_WIDGET = 772;
    private static final int REQ_CONFIGURE_WIDGET = 773;

    /** Reserved {@link HomeItem#page} value for the small widget area embedded in the search page itself. */
    private static final int PAGE_SEARCH_WIDGETS = -1;

    private Prefs prefs;
    private UsageStore usageStore;
    private ShortcutStore shortcutStore;
    private HomeLayoutStore layoutStore;
    private SearchEngine searchEngine;
    private WidgetHostController widgetHostController;

    private HorizontalPager pager;
    private FrameLayout pagerRoot;
    private View searchPageView;
    private LinearLayout overviewBar;
    private TextView overviewTitle;
    private boolean overview;
    private LinearLayout pageIndicator;
    private EditText searchInput;
    private View resultsOverlay;
    private ListView resultsList;
    private TextView emptyText;
    private FrameLayout idleWidgetArea;
    private HorizontalScrollView dockScroll;
    private LinearLayout dockContainer;
    private SearchResultAdapter searchAdapter;
    private HomeGridLayout searchWidgetGrid;

    private final List<HomeGridLayout> gridPages = new ArrayList<>();
    private List<HomeItem> homeItems = new ArrayList<>();
    private List<AppInfo> rawApps = new ArrayList<>();
    private List<AppInfo> currentApps = new ArrayList<>();
    private List<ContactInfo> currentContacts = new ArrayList<>();
    private List<SearchResult> currentResults = new ArrayList<>();

    private int pendingWidgetPage, pendingWidgetCol, pendingWidgetRow, pendingWidgetId = -1;
    private String pendingDirectCallNumber;
    private int builtLayoutRevision;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        usageStore = UsageStore.get(this);
        shortcutStore = ShortcutStore.get(this);
        layoutStore = HomeLayoutStore.get(this);
        searchEngine = new SearchEngine(usageStore, SearchLearningStore.get(this));
        widgetHostController = WidgetHostController.get(this);

        setContentView(R.layout.activity_main);
        builtLayoutRevision = prefs.layoutRevision();
        buildPages();

        AppIndexer.get(this).addListener(this);
        ContactIndexer.get(this).addListener(this);
        ensureContactsSearchable();
    }

    /** Contacts show up in search automatically once permission is granted; ask exactly once. */
    private void ensureContactsSearchable() {
        if (PermissionUtil.has(this, android.Manifest.permission.READ_CONTACTS)) {
            ContactIndexer.get(this).loadAsync();
        } else if (!prefs.contactsPermissionAsked()) {
            prefs.setContactsPermissionAsked(true);
            new AlertDialog.Builder(this)
                    .setTitle("Search your contacts?")
                    .setMessage("Piko can show contacts in search and let you call or message them. Contacts stay on your device. You can change this later in Settings › Permissions.")
                    .setPositiveButton("Allow", (d, w) -> PermissionUtil.requestContacts(this))
                    .setNegativeButton("Not now", null)
                    .show();
        }
    }

    // ---------------------------------------------------------------- pages

    private void buildPages() {
        pagerRoot = findViewById(R.id.pager_root);
        pageIndicator = findViewById(R.id.page_indicator);

        pager = new HorizontalPager(this);
        pagerRoot.addView(pager, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeItems = layoutStore.loadAll();

        View searchPage = buildSearchPage();
        searchPageView = searchPage;
        pager.addView(searchPage);

        int pageCount = Math.max(1, prefs.gridPageCount());
        for (int i = 0; i < pageCount; i++) {
            HomeGridLayout grid = createGrid(i, prefs.columns(), prefs.rows());
            gridPages.add(grid);
            pager.addView(grid);
        }

        buildPageIndicator(1 + pageCount);
        pager.setOnPageChangeListener(index -> {
            updatePageIndicator(index);
            updateOverviewTitle();
        });
        buildOverviewBar();

        pager.post(() -> pager.setCurrentItem(prefs.defaultPage(), false));
        pager.post(this::updateIndicatorInitial);
    }

    private View buildSearchPage() {
        View page = LayoutInflater.from(this).inflate(R.layout.page_search, null, false);
        searchInput = page.findViewById(R.id.search_input);
        resultsList = page.findViewById(R.id.results_list);
        emptyText = page.findViewById(R.id.empty_text);
        idleWidgetArea = page.findViewById(R.id.idle_widget_area);
        dockScroll = page.findViewById(R.id.dock_scroll);
        dockContainer = page.findViewById(R.id.dock_container);
        ImageButton settingsButton = page.findViewById(R.id.btn_settings);
        ImageButton moreButton = page.findViewById(R.id.btn_more);

        // The search bar is shared by every page, so it lives outside the pager.
        View searchBar = page.findViewById(R.id.search_bar);
        ((ViewGroup) searchBar.getParent()).removeView(searchBar);
        ((ViewGroup) findViewById(R.id.search_bar_container)).addView(searchBar);

        // Results are an overlay above the pager, shown only while typing; page 0 is a normal grid page.
        resultsOverlay = page.findViewById(R.id.results_overlay);
        ((ViewGroup) resultsOverlay.getParent()).removeView(resultsOverlay);
        resultsOverlay.setVisibility(View.GONE);
        ((FrameLayout) findViewById(R.id.pager_root)).addView(resultsOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        searchWidgetGrid = createGrid(PAGE_SEARCH_WIDGETS, prefs.columns(), prefs.rows());
        idleWidgetArea.addView(searchWidgetGrid);

        searchAdapter = new SearchResultAdapter(this, new SearchResultAdapter.Listener() {
            @Override
            public void onCall(String phoneNumber) {
                doCall(phoneNumber);
            }

            @Override
            public void onContactActed(ContactInfo contact) {
                searchEngine.recordChoice(searchInput.getText().toString(), SearchEngine.contactKey(contact));
            }

            @Override
            public void onWebSearch(String query, String engineId) {
                IntentUtil.webSearch(MainActivity.this, engineId, query);
            }
        });
        resultsList.setAdapter(searchAdapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndShow(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnter) {
                activateBestResult();
                return true;
            }
            return false;
        });

        resultsList.setOnItemClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            SearchResult result = searchAdapter.getResultAt(position);
            if (result.type == SearchResult.TYPE_APP) {
                launchApp(result.app);
            } else if (result.type == SearchResult.TYPE_ACTION) {
                result.action.run();
            } else if (result.type == SearchResult.TYPE_WEB) {
                IntentUtil.webSearch(this, primaryEngine(), result.text);
            } else if (result.type == SearchResult.TYPE_CALC && result.value != null) {
                IntentUtil.copyToClipboard(this, result.text, result.value);
            }
        });

        // The results overlay is not inside the pager, so only vertical drift matters, but still
        // veto the long-press if the finger moved so scrolling the list never opens a menu.
        final float[] listDown = new float[2];
        final boolean[] listMoved = new boolean[1];
        final int listSlop = Math.max(4, android.view.ViewConfiguration.get(this).getScaledTouchSlop() / 2);
        resultsList.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                listDown[0] = ev.getX();
                listDown[1] = ev.getY();
                listMoved[0] = false;
            } else if (ev.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
                    && (Math.abs(ev.getX() - listDown[0]) > listSlop || Math.abs(ev.getY() - listDown[1]) > listSlop)) {
                listMoved[0] = true;
            }
            return false;
        });
        resultsList.setOnItemLongClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            if (listMoved[0]) return true;
            SearchResult result = searchAdapter.getResultAt(position);
            if (result.type == SearchResult.TYPE_APP) {
                showAppOptionsMenu(v, result.app);
                return true;
            }
            return false;
        });

        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        moreButton.setOnClickListener(this::showMoreMenu);

        return page;
    }

    /** Enter/search-action activates the top-ranked match — the one rendered nearest the search bar. */
    private void activateBestResult() {
        if (currentResults.isEmpty()) return;
        SearchResult best = currentResults.get(0);
        if (best.type == SearchResult.TYPE_APP) {
            launchApp(best.app);
        } else if (best.type == SearchResult.TYPE_ACTION) {
            best.action.run();
        } else if (best.type == SearchResult.TYPE_CONTACT) {
            doCall(best.contact.phoneNumber);
            searchEngine.recordChoice(searchInput.getText().toString(), SearchEngine.contactKey(best.contact));
        } else if (best.type == SearchResult.TYPE_WEB) {
            IntentUtil.webSearch(this, primaryEngine(), best.text);
        }
        // TYPE_CALC: enter does nothing extra — tapping the row copies the value.
    }

    /** The first enabled engine, used when the user taps the web-search row itself rather than an engine chip. */
    private String primaryEngine() {
        List<String> enabled = prefs.enabledSearchEngines();
        return enabled.isEmpty() ? "google" : enabled.get(0);
    }

    private HomeGridLayout createGrid(int pageId, int columns, int rows) {
        HomeGridLayout grid = new HomeGridLayout(this, columns, rows);
        grid.setFreeform(prefs.freeform());
        grid.setCallbacks(new HomeGridLayout.Callbacks() {
            @Override
            public void onEmptyCellLongPress(int col, int row) {
                if (pageId == PAGE_SEARCH_WIDGETS || overview) showAddItemMenu(grid, pageId, col, row);
                else enterOverview();
            }

            @Override
            public int[] actionsFor(View child) {
                HomeItem item = (HomeItem) child.getTag();
                return item.type == HomeItem.TYPE_APP
                        ? new int[]{HomeGridLayout.ACTION_REMOVE, HomeGridLayout.ACTION_UNINSTALL, HomeGridLayout.ACTION_INFO,
                                HomeGridLayout.ACTION_EDIT, HomeGridLayout.ACTION_NAME}
                        : new int[]{HomeGridLayout.ACTION_REMOVE, HomeGridLayout.ACTION_RESIZE};
            }

            @Override
            public void onItemAction(View child, int action) {
                HomeItem item = (HomeItem) child.getTag();
                switch (action) {
                    case HomeGridLayout.ACTION_REMOVE:
                        removeHomeItem(grid, item, child);
                        break;
                    case HomeGridLayout.ACTION_UNINSTALL:
                        IntentUtil.uninstall(MainActivity.this, item.packageName);
                        break;
                    case HomeGridLayout.ACTION_INFO:
                        IntentUtil.openAppInfo(MainActivity.this, item.packageName);
                        break;
                    case HomeGridLayout.ACTION_EDIT:
                        com.piko.launcher.ui.settings.AppEditActivity.start(MainActivity.this, toAppInfo(item));
                        break;
                    case HomeGridLayout.ACTION_RESIZE:
                        resizeWidget(grid, item, child);
                        break;
                    case HomeGridLayout.ACTION_NAME:
                        toggleLabel(child, item);
                        break;
                }
            }

            @Override
            public void onItemMoved(View child, int newCol, int newRow) {
                HomeItem item = (HomeItem) child.getTag();
                item.col = newCol;
                item.row = newRow;
                HomeGridLayout.GridLayoutParams lp = (HomeGridLayout.GridLayoutParams) child.getLayoutParams();
                item.fx = lp.fx;
                item.fy = lp.fy;
                layoutStore.saveAll(homeItems);
            }

            @Override
            public void onItemRemoveRequested(View child) {
                HomeItem item = (HomeItem) child.getTag();
                removeHomeItem(grid, item, child);
            }

            @Override
            public HomeGridLayout onItemDragToPage(View child, int direction) {
                return moveItemToAdjacentPage(grid, child, direction);
            }

            @Override
            public void onItemResized(View child) {
                HomeItem item = (HomeItem) child.getTag();
                HomeGridLayout.GridLayoutParams lp = (HomeGridLayout.GridLayoutParams) child.getLayoutParams();
                item.colSpan = lp.colSpan;
                item.rowSpan = lp.rowSpan;
                layoutStore.saveAll(homeItems);
            }

            @Override
            public void onItemLongPress(View child) {
                showHomeItemMenu(grid, child);
            }
        });
        populateGridPage(grid, pageId);
        return grid;
    }

    private HomeGridLayout.GridLayoutParams paramsFor(HomeGridLayout grid, HomeItem item) {
        HomeGridLayout.GridLayoutParams lp = grid.gridParams(item.col, item.row, item.colSpan, item.rowSpan);
        lp.fx = item.fx;
        lp.fy = item.fy;
        return lp;
    }

    private void toggleLabel(View child, HomeItem item) {
        com.piko.launcher.core.AppCustomizationStore store = com.piko.launcher.core.AppCustomizationStore.get(this);
        String key = item.packageName + "/" + item.activityName;
        boolean hide = !store.hidesLabel(key);
        store.setHideLabel(key, hide);
        View label = child.findViewById(R.id.label);
        if (label != null) label.setVisibility(hide || prefs.hideAllLabels() ? View.GONE : View.VISIBLE);
    }

    private void populateGridPage(HomeGridLayout grid, int pageIndex) {
        for (HomeItem item : homeItems) {
            if (item.page != pageIndex) continue;
            if (item.type == HomeItem.TYPE_APP) {
                View view = HomeIconViewFactory.createAppIcon(this, item);
                view.setOnClickListener(v -> IntentUtil.launchApp(this, toAppInfo(item)));
                grid.addView(view, paramsFor(grid, item));
            } else {
                AppWidgetProviderInfo info = widgetHostController.infoFor(item.appWidgetId);
                if (info == null) continue;
                AppWidgetHostView hostView;
                try {
                    hostView = widgetHostController.createView(this, item.appWidgetId, info);
                } catch (Exception e) {
                    continue;
                }
                hostView.setTag(item);
                grid.addView(hostView, paramsFor(grid, item));
            }
        }
    }

    private void buildPageIndicator(int totalPages) {
        pageIndicator.removeAllViews();
        int dotSize = (int) (7 * getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < totalPages; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_dot_indicator);
            pageIndicator.addView(dot);
        }
    }

    private void updateIndicatorInitial() {
        updatePageIndicator(pager.getCurrentItem());
    }

    private void updatePageIndicator(int index) {
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            pageIndicator.getChildAt(i).setBackgroundResource(
                    i == index ? R.drawable.bg_dot_indicator_active : R.drawable.bg_dot_indicator);
        }
    }

    // -------------------------------------------------------------- search

    private void filterAndShow(String query) {
        boolean idle = query.isEmpty();
        if (!idle && overview) exitOverview();
        currentResults = searchEngine.searchAll(
                prefs.flag(Prefs.FLAG_SEARCH_APPS) ? currentApps : new ArrayList<>(),
                prefs.flag(Prefs.FLAG_SEARCH_CONTACTS) ? currentContacts : new ArrayList<>(), query);

        if (!idle) {
            String trimmed = query.trim();
            if (prefs.flag(Prefs.FLAG_SEARCH_CURRENCY) && com.piko.launcher.core.CurrencyConverter.looksLikeConversion(trimmed)) {
                currentResults.add(0, SearchResult.forCalc(trimmed, null));
                com.piko.launcher.core.CurrencyConverter.convertAsync(trimmed, new com.piko.launcher.core.CurrencyConverter.Callback() {
                    @Override
                    public void onResult(String forQuery, double convertedValue, String toCurrency) {
                        if (!forQuery.equals(searchInput.getText().toString().trim())) return;
                        currentResults.set(0, SearchResult.forCalc(forQuery,
                                com.piko.launcher.core.MathEval.format(convertedValue) + " " + toCurrency));
                        renderResults(idle);
                    }

                    @Override
                    public void onFailure(String forQuery) {
                    }
                });
            } else if (prefs.flag(Prefs.FLAG_SEARCH_CALC) && com.piko.launcher.core.MathEval.looksLikeExpression(trimmed)) {
                Double value = com.piko.launcher.core.MathEval.evaluate(trimmed);
                if (value != null) {
                    currentResults.add(0, SearchResult.forCalc(trimmed, com.piko.launcher.core.MathEval.format(value)));
                }
            }
            if (prefs.flag(Prefs.FLAG_SEARCH_CONTACTS) && trimmed.matches("\\+?[0-9][0-9\\s\\-().]*")
                    && trimmed.replaceAll("[^0-9]", "").length() >= 3 && !hasContactWithNumber(trimmed)) {
                // Not in the address book: still offer call/message for the typed number.
                int at = !currentResults.isEmpty() && currentResults.get(0).type == SearchResult.TYPE_CALC ? 1 : 0;
                currentResults.add(at, SearchResult.forContact(new ContactInfo(-1, trimmed, trimmed)));
            }
            if (prefs.flag(Prefs.FLAG_SEARCH_WEB)) {
                for (String engineId : prefs.enabledSearchEngines()) {
                    com.piko.launcher.core.WebSearchEngines.Engine e = prefs.engineById(engineId);
                    currentResults.add(SearchResult.forAction(e.label.substring(0, 1), getString(R.string.search_engine_for, e.label),
                            trimmed, () -> IntentUtil.webSearch(this, engineId, trimmed)));
                }
            }
        }
        currentResults = explodeContacts(currentResults, query);

        renderResults(idle);
    }

    /** Turns each contact into one row per enabled action (call, SMS, WhatsApp…), keeping ranking order. */
    private List<SearchResult> explodeContacts(List<SearchResult> in, String query) {
        List<SearchResult> out = new ArrayList<>();
        for (SearchResult r : in) {
            if (r.type != SearchResult.TYPE_CONTACT) {
                out.add(r);
                continue;
            }
            final ContactInfo c = r.contact;
            final String who = c.name.equals(c.phoneNumber) ? c.phoneNumber : c.name;
            final String sub = c.name.equals(c.phoneNumber) ? "" : c.phoneNumber;
            final String q = query;
            if (prefs.flag(Prefs.FLAG_ACTION_CALL)) out.add(contactAction("☎", getString(R.string.act_call, who), sub, c, q,
                    () -> doCall(c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_SMS)) out.add(contactAction("✉", getString(R.string.act_sms, who), sub, c, q,
                    () -> IntentUtil.sendSms(this, c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_WA_MESSAGE)) out.add(contactAction("W", getString(R.string.act_wa_message, who), sub, c, q,
                    () -> IntentUtil.whatsAppMessage(this, c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_WA_CALL)) out.add(contactAction("W☎", getString(R.string.act_wa_call, who), sub, c, q,
                    () -> IntentUtil.whatsAppCall(this, c.phoneNumber, false)));
            if (c.contactId >= 0) out.add(contactAction("i", getString(R.string.act_open_contact, who), sub, c, q,
                    () -> IntentUtil.openContactInfo(this, c.contactId)));
            out.add(contactAction("⧉", getString(R.string.act_copy_number, who), sub, c, q,
                    () -> IntentUtil.copyToClipboard(this, "number", c.phoneNumber)));
        }
        return out;
    }

    private SearchResult contactAction(String badge, String title, String sub, ContactInfo c, String query, Runnable run) {
        return SearchResult.forAction(badge, title, sub, () -> {
            run.run();
            if (c.contactId >= 0) searchEngine.recordChoice(query, SearchEngine.contactKey(c));
        });
    }

    private boolean hasContactWithNumber(String typed) {
        String digits = typed.replaceAll("[^0-9]", "");
        for (SearchResult r : currentResults) {
            if (r.type == SearchResult.TYPE_CONTACT
                    && r.contact.phoneNumber.replaceAll("[^0-9]", "").endsWith(digits)) return true;
        }
        return false;
    }

    private void renderResults(boolean idle) {
        // Best match renders nearest the search bar: reverse so it lands last in a stackFromBottom list.
        List<SearchResult> display = new ArrayList<>(currentResults);
        Collections.reverse(display);
        searchAdapter.submit(display, searchInput.getText().toString());

        boolean hasResults = !currentResults.isEmpty();
        resultsOverlay.setVisibility(idle ? View.GONE : View.VISIBLE);
        resultsList.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        emptyText.setVisibility(!hasResults ? View.VISIBLE : View.GONE);
        refreshShortcutsRow(idle);
    }

    private void launchApp(AppInfo app) {
        usageStore.recordLaunch(app.key());
        searchEngine.recordChoice(searchInput.getText().toString(), app.key());
        IntentUtil.launchApp(this, app);
        searchInput.setText("");
        hideKeyboard();
    }

    private void showAppOptionsMenu(View anchor, AppInfo app) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_to_home);
        menu.getMenu().add(0, 2, 1, R.string.menu_app_info);
        menu.getMenu().add(0, 3, 2, R.string.menu_edit_app);
        menu.getMenu().add(0, 4, 3, R.string.menu_uninstall);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    addAppToFirstFreeCell(app);
                    return true;
                case 2:
                    IntentUtil.openAppInfo(this, app.packageName);
                    return true;
                case 3:
                    com.piko.launcher.ui.settings.AppEditActivity.start(this, app);
                    return true;
                case 4:
                    IntentUtil.uninstall(this, app.packageName);
                    return true;
            }
            return false;
        });
        menu.show();
    }

    // --------------------------------------------------------- add to grid

    private HomeGridLayout gridForPage(int pageId) {
        return pageId == PAGE_SEARCH_WIDGETS ? searchWidgetGrid : gridPages.get(pageId);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_app);
        menu.getMenu().add(0, 2, 1, R.string.menu_add_widget);
        menu.getMenu().add(0, 3, 2, R.string.menu_manage_apps);
        menu.setOnMenuItemClickListener(m -> {
            int idx = pager.getCurrentItem();
            int pageId = idx == 0 ? PAGE_SEARCH_WIDGETS : idx - 1;
            if (m.getItemId() == 3) {
                showAppChooser((app, v, dialog) -> showAppOptionsMenu(v, app));
                return true;
            }
            int[] free = findFreeArea(gridForPage(pageId), 1, 1);
            if (free == null) {
                Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (m.getItemId() == 1) {
                showAppPickerDialog(pageId, free[0], free[1]);
            } else {
                pendingWidgetPage = pageId;
                pendingWidgetCol = free[0];
                pendingWidgetRow = free[1];
                startActivityForResult(new Intent(this, WidgetPickerActivity.class), REQ_PICK_WIDGET_PROVIDER);
            }
            return true;
        });
        menu.show();
    }

    private void showAddItemMenu(HomeGridLayout grid, int pageIndex, int col, int row) {
        PopupMenu menu = new PopupMenu(this, grid);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_app);
        menu.getMenu().add(0, 2, 1, R.string.menu_add_widget);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showAppPickerDialog(pageIndex, col, row);
            } else {
                pendingWidgetPage = pageIndex;
                pendingWidgetCol = col;
                pendingWidgetRow = row;
                startActivityForResult(new Intent(this, WidgetPickerActivity.class), REQ_PICK_WIDGET_PROVIDER);
            }
            return true;
        });
        menu.show();
    }

    private void addAppToFirstFreeCell(AppInfo app) {
        for (int p = 0; p < gridPages.size(); p++) {
            HomeGridLayout grid = gridPages.get(p);
            for (int r = 0; r < grid.getRows(); r++) {
                for (int c = 0; c < grid.getColumns(); c++) {
                    if (grid.isAreaFree(c, r, 1, 1, null)) {
                        HomeItem item = new HomeItem();
                        item.type = HomeItem.TYPE_APP;
                        item.page = p;
                        item.col = c;
                        item.row = r;
                        item.packageName = app.packageName;
                        item.activityName = app.activityName;
                        item.label = app.label;
                        homeItems.add(item);
                        layoutStore.saveAll(homeItems);
                        View view = HomeIconViewFactory.createAppIcon(this, item);
                        view.setOnClickListener(v -> IntentUtil.launchApp(this, toAppInfo(item)));
                        grid.addView(view, grid.gridParams(c, r, 1, 1));
                        Toast.makeText(this, R.string.menu_add_to_home, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        }
    }

    private interface AppChooserListener {
        void onPick(AppInfo app, View anchor, AlertDialog dialog);
    }

    private void showAppPickerDialog(int pageIndex, int col, int row) {
        showAppChooser((app, v, dialog) -> {
            HomeItem item = new HomeItem();
            item.type = HomeItem.TYPE_APP;
            item.page = pageIndex;
            item.col = col;
            item.row = row;
            item.packageName = app.packageName;
            item.activityName = app.activityName;
            item.label = app.label;
            homeItems.add(item);
            layoutStore.saveAll(homeItems);

            HomeGridLayout grid = gridForPage(pageIndex);
            View view = HomeIconViewFactory.createAppIcon(this, item);
            view.setOnClickListener(v2 -> IntentUtil.launchApp(this, toAppInfo(item)));
            grid.addView(view, grid.gridParams(col, row, 1, 1));

            dialog.dismiss();
        });
    }

    private void showAppChooser(AppChooserListener listener) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.page_search, null, false);
        EditText input = dialogView.findViewById(R.id.search_input);
        ListView list = dialogView.findViewById(R.id.results_list);
        dialogView.findViewById(R.id.btn_settings).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btn_more).setVisibility(View.GONE);
        dialogView.findViewById(R.id.dock_scroll).setVisibility(View.GONE);
        dialogView.findViewById(R.id.idle_widget_area).setVisibility(View.GONE);

        AppListAdapter adapter = new AppListAdapter(this);
        adapter.submit(searchEngine.search(currentApps, ""));
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.submit(searchEngine.search(currentApps, s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        list.setOnItemClickListener((parent, v, position, id) ->
                listener.onPick(adapter.getAppAt(position), v, dialog));

        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_WIDGET_PROVIDER) {
            if (resultCode != RESULT_OK || data == null) return;
            ComponentName provider = data.getParcelableExtra(WidgetPickerActivity.EXTRA_PROVIDER);
            if (provider == null) return;
            startWidgetBind(provider);
        } else if (requestCode == REQ_BIND_WIDGET) {
            if (resultCode == RESULT_OK) {
                proceedAfterWidgetBind(pendingWidgetId);
            } else {
                widgetHostController.deleteAppWidgetId(pendingWidgetId);
            }
        } else if (requestCode == REQ_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK) {
                placeWidget(pendingWidgetId);
            } else {
                widgetHostController.deleteAppWidgetId(pendingWidgetId);
            }
        }
    }

    /**
     * Allocates a widget id for the provider chosen in {@link WidgetPickerActivity} and binds it.
     * Most providers bind silently via {@code bindAppWidgetIdIfAllowed}; a few require the user to
     * confirm via the system's {@code ACTION_APPWIDGET_BIND} permission screen.
     */
    private void startWidgetBind(ComponentName provider) {
        int widgetId = widgetHostController.allocateAppWidgetId();
        pendingWidgetId = widgetId;
        if (widgetHostController.manager().bindAppWidgetIdIfAllowed(widgetId, provider)) {
            proceedAfterWidgetBind(widgetId);
        } else {
            Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
            startActivityForResult(bindIntent, REQ_BIND_WIDGET);
        }
    }

    private void proceedAfterWidgetBind(int widgetId) {
        AppWidgetProviderInfo info = widgetHostController.infoFor(widgetId);
        if (info == null) {
            widgetHostController.deleteAppWidgetId(widgetId);
            return;
        }
        if (info.configure != null) {
            Intent configureIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            configureIntent.setComponent(info.configure);
            configureIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            try {
                startActivityForResult(configureIntent, REQ_CONFIGURE_WIDGET);
                return;
            } catch (Exception e) {
                // Declared a configure activity that can't actually be launched; fall through to defaults.
            }
        }
        placeWidget(widgetId);
    }

    private void placeWidget(int widgetId) {
        AppWidgetProviderInfo info = widgetHostController.infoFor(widgetId);
        if (info == null) {
            widgetHostController.deleteAppWidgetId(widgetId);
            return;
        }
        HomeGridLayout grid = gridForPage(pendingWidgetPage);
        int cellSize = grid.getCellSize() > 0 ? grid.getCellSize() : 1;
        // AppWidgetProviderInfo.minWidth/minHeight are in dp; cellSize is in px.
        float density = getResources().getDisplayMetrics().density;
        int minWidthPx = Math.round(info.minWidth * density);
        int minHeightPx = Math.round(info.minHeight * density);
        int colSpan = Math.max(1, Math.min(grid.getColumns(), (int) Math.ceil(minWidthPx / (double) cellSize)));
        int rowSpan = Math.max(1, Math.min(grid.getRows(), (int) Math.ceil(minHeightPx / (double) cellSize)));

        int col = pendingWidgetCol;
        int row = pendingWidgetRow;
        if (!grid.isAreaFree(col, row, colSpan, rowSpan, null)) {
            int[] free = findFreeArea(grid, colSpan, rowSpan);
            if (free == null) {
                Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
                widgetHostController.deleteAppWidgetId(widgetId);
                return;
            }
            col = free[0];
            row = free[1];
        }

        HomeItem item = new HomeItem();
        item.type = HomeItem.TYPE_WIDGET;
        item.page = pendingWidgetPage;
        item.col = col;
        item.row = row;
        item.colSpan = colSpan;
        item.rowSpan = rowSpan;
        item.appWidgetId = widgetId;
        homeItems.add(item);
        layoutStore.saveAll(homeItems);

        AppWidgetHostView hostView;
        try {
            hostView = widgetHostController.createView(this, widgetId, info);
        } catch (Exception e) {
            homeItems.remove(item);
            layoutStore.saveAll(homeItems);
            widgetHostController.deleteAppWidgetId(widgetId);
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        hostView.setTag(item);
        grid.addView(hostView, grid.gridParams(col, row, colSpan, rowSpan));
    }

    private int[] findFreeArea(HomeGridLayout grid, int colSpan, int rowSpan) {
        for (int r = 0; r <= grid.getRows() - rowSpan; r++) {
            for (int c = 0; c <= grid.getColumns() - colSpan; c++) {
                if (grid.isAreaFree(c, r, colSpan, rowSpan, null)) return new int[]{c, r};
            }
        }
        return null;
    }

    private void showHomeItemMenu(HomeGridLayout grid, View child) {
        HomeItem item = (HomeItem) child.getTag();
        PopupMenu menu = new PopupMenu(this, child);
        int id = 0;
        final int idInfo = item.type == HomeItem.TYPE_APP ? ++id : -1;
        final int idEdit = item.type == HomeItem.TYPE_APP ? ++id : -1;
        final int idUninstall = item.type == HomeItem.TYPE_APP ? ++id : -1;
        final int idResize = item.type == HomeItem.TYPE_WIDGET ? ++id : -1;
        final int idName = item.type == HomeItem.TYPE_APP ? ++id : -1;
        final int idMovePage = gridPages.size() > 1 ? ++id : -1;
        final int idRemove = ++id;

        if (idInfo != -1) menu.getMenu().add(0, idInfo, 0, R.string.menu_app_info);
        if (idEdit != -1) menu.getMenu().add(0, idEdit, 1, R.string.menu_edit_app);
        if (idUninstall != -1) menu.getMenu().add(0, idUninstall, 2, R.string.menu_uninstall);
        if (idResize != -1) menu.getMenu().add(0, idResize, 3, R.string.menu_resize_widget);
        if (idName != -1) menu.getMenu().add(0, idName, 3, com.piko.launcher.core.AppCustomizationStore.get(this)
                .hidesLabel(item.packageName + "/" + item.activityName) ? R.string.menu_show_name : R.string.menu_hide_name);
        if (idMovePage != -1) menu.getMenu().add(0, idMovePage, 4, R.string.menu_add_to_home);
        menu.getMenu().add(0, idRemove, 5, R.string.menu_remove_from_home);

        menu.setOnMenuItemClickListener(m -> {
            int clicked = m.getItemId();
            if (clicked == idInfo) {
                IntentUtil.openAppInfo(this, item.packageName);
            } else if (clicked == idEdit) {
                com.piko.launcher.ui.settings.AppEditActivity.start(this, toAppInfo(item));
            } else if (clicked == idUninstall) {
                IntentUtil.uninstall(this, item.packageName);
            } else if (clicked == idResize) {
                resizeWidget(grid, item, child);
            } else if (clicked == idName) {
                toggleLabel(child, item);
            } else if (clicked == idMovePage) {
                showMovePagePicker(grid, item, child);
            } else if (clicked == idRemove) {
                confirmRemoveHomeItem(grid, item, child);
            }
            return true;
        });
        menu.show();
    }

    /** Drag-to-edge page move: keeps the cell if free on the neighbouring page, else the first free area. */
    private HomeGridLayout moveItemToAdjacentPage(HomeGridLayout source, View child, int direction) {
        HomeItem item = (HomeItem) child.getTag();
        int targetIdx = item.page + 1 + direction;
        if (targetIdx < 0) return null;
        if (targetIdx > gridPages.size()) {
            // Dragged past the last page: grow a fresh one on the fly (up to a sane limit).
            if (gridPages.size() >= 8) return null;
            HomeGridLayout fresh = createGrid(gridPages.size(), prefs.columns(), prefs.rows());
            gridPages.add(fresh);
            pager.addView(fresh);
            prefs.setGridPageCount(gridPages.size());
            builtLayoutRevision = prefs.layoutRevision();
            buildPageIndicator(1 + gridPages.size());
            updatePageIndicator(pager.getCurrentItem());
        }
        int targetPage = targetIdx - 1;
        HomeGridLayout target = gridForPage(targetPage);
        int[] cell = prefs.freeform() || target.isAreaFree(item.col, item.row, item.colSpan, item.rowSpan, null)
                ? new int[]{item.col, item.row}
                : findFreeArea(target, item.colSpan, item.rowSpan);
        if (cell == null) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return null;
        }
        source.removeView(child);
        item.page = targetPage;
        item.col = cell[0];
        item.row = cell[1];
        layoutStore.saveAll(homeItems);
        target.addView(child, paramsFor(target, item));
        pager.setCurrentItem(targetIdx, true);
        return target;
    }


    // ------------------------------------------------------------ overview

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void buildOverviewBar() {
        overviewBar = new LinearLayout(this);
        overviewBar.setOrientation(LinearLayout.VERTICAL);
        overviewBar.setBackgroundColor(getResources().getColor(R.color.piko_bg));
        overviewBar.setPadding(dp(8), dp(8), dp(8), dp(12));
        overviewBar.setVisibility(View.GONE);

        overviewTitle = new TextView(this);
        overviewTitle.setTextColor(getResources().getColor(R.color.piko_text_secondary));
        overviewTitle.setTextSize(12);
        overviewTitle.setGravity(android.view.Gravity.CENTER);
        overviewTitle.setPadding(0, 0, 0, dp(6));
        overviewBar.addView(overviewTitle);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(ovButton("＋", R.string.ov_add_page, v -> addPage()));
        row.addView(ovButton("🗑", R.string.ov_delete_page, v -> deletePage()));
        row.addView(ovButton("◀", R.string.ov_move_left, v -> movePage(-1)));
        row.addView(ovButton("▶", R.string.ov_move_right, v -> movePage(1)));
        row.addView(ovButton("▦", R.string.ov_tidy, v -> tidyPage()));
        row.addView(ovButton("⊕", R.string.ov_add_item, v -> {
            int g = pager.getCurrentItem() - 1;
            if (g >= 0) showAddItemMenu(gridPages.get(g), g, 0, 0);
        }));
        row.addView(ovButton("✓", R.string.ov_done, v -> exitOverview()));
        overviewBar.addView(row);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        ((FrameLayout) findViewById(R.id.root)).addView(overviewBar, lp);
    }

    private View ovButton(String glyph, int label, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(android.view.Gravity.CENTER);
        b.setBackgroundResource(R.drawable.bg_pressable);
        b.setPadding(0, dp(6), 0, dp(6));
        TextView g = new TextView(this);
        g.setText(glyph);
        g.setTextSize(20);
        g.setTextColor(getResources().getColor(R.color.piko_text_primary));
        g.setGravity(android.view.Gravity.CENTER);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(10.5f);
        t.setTextColor(getResources().getColor(R.color.piko_text_secondary));
        t.setGravity(android.view.Gravity.CENTER);
        b.addView(g);
        b.addView(t);
        b.setOnClickListener(click);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return b;
    }

    private void enterOverview() {
        if (overview) return;
        overview = true;
        hideKeyboard();
        pagerRoot.setBackgroundResource(R.drawable.bg_page_card);
        pagerRoot.animate().scaleX(0.78f).scaleY(0.78f).translationY(-dp(14)).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        overviewBar.setAlpha(0f);
        overviewBar.setVisibility(View.VISIBLE);
        overviewBar.animate().alpha(1f).setDuration(240).start();
        pager.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        updateOverviewTitle();
    }

    private void exitOverview() {
        if (!overview) return;
        overview = false;
        pagerRoot.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(220)
                .withEndAction(() -> pagerRoot.setBackground(null)).start();
        overviewBar.animate().alpha(0f).setDuration(180).withEndAction(() -> overviewBar.setVisibility(View.GONE)).start();
    }

    private void updateOverviewTitle() {
        if (overviewTitle == null) return;
        int idx = pager.getCurrentItem();
        overviewTitle.setText(idx == 0 ? "Search page" : "Page " + idx + " of " + gridPages.size());
    }

    /** Rebuilds every grid page from {@link #homeItems} after a structural change, then focuses one. */
    private void rebuildGridPages(int count, int focusIndex) {
        for (HomeGridLayout g : gridPages) pager.removeView(g);
        gridPages.clear();
        prefs.setGridPageCount(count);
        builtLayoutRevision = prefs.layoutRevision();
        for (int i = 0; i < count; i++) {
            HomeGridLayout grid = createGrid(i, prefs.columns(), prefs.rows());
            gridPages.add(grid);
            pager.addView(grid);
        }
        buildPageIndicator(1 + count);
        layoutStore.saveAll(homeItems);
        pager.setCurrentItem(Math.max(0, Math.min(focusIndex, count)), false);
        updatePageIndicator(pager.getCurrentItem());
        updateOverviewTitle();
    }

    private void addPage() {
        if (gridPages.size() >= 8) {
            Toast.makeText(this, "Up to 8 pages", Toast.LENGTH_SHORT).show();
            return;
        }
        int at = pager.getCurrentItem();   // new grid page index = at (inserted right after the current one)
        for (HomeItem it : homeItems) if (it.page >= at) it.page++;
        rebuildGridPages(gridPages.size() + 1, at + 1);
    }

    private void deletePage() {
        final int g = pager.getCurrentItem() - 1;
        if (g < 0) return;
        if (gridPages.size() <= 1) {
            Toast.makeText(this, "Can't delete the last page", Toast.LENGTH_SHORT).show();
            return;
        }
        int n = 0;
        for (HomeItem it : homeItems) if (it.page == g) n++;
        final int count = n;
        new AlertDialog.Builder(this)
                .setTitle("Delete page " + (g + 1) + "?")
                .setMessage(count == 0 ? "This page is empty." : count + " item(s) on it will be removed.")
                .setPositiveButton(R.string.action_remove, (d, w) -> {
                    List<HomeItem> keep = new ArrayList<>();
                    for (HomeItem it : homeItems) {
                        if (it.page == g) {
                            if (it.type == HomeItem.TYPE_WIDGET) widgetHostController.deleteAppWidgetId(it.appWidgetId);
                        } else {
                            if (it.page > g) it.page--;
                            keep.add(it);
                        }
                    }
                    homeItems = keep;
                    rebuildGridPages(gridPages.size() - 1, Math.min(g + 1, gridPages.size() - 1));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void movePage(int dir) {
        int g = pager.getCurrentItem() - 1;
        int t = g + dir;
        if (g < 0 || t < 0 || t >= gridPages.size()) return;
        for (HomeItem it : homeItems) {
            if (it.page == g) it.page = t;
            else if (it.page == t) it.page = g;
        }
        rebuildGridPages(gridPages.size(), t + 1);
    }

    /** Snaps every item on the page back onto the grid, top-to-bottom and left-to-right. */
    private void tidyPage() {
        int g = pager.getCurrentItem() - 1;
        if (g < 0) return;
        final int cols = prefs.columns(), rws = prefs.rows();
        final float cw = 1f / cols, rh = 1f / rws;
        List<HomeItem> onPage = new ArrayList<>();
        for (HomeItem it : homeItems) if (it.page == g) onPage.add(it);
        java.util.Collections.sort(onPage, (a, b) -> {
            float ay = a.fy >= 0 ? a.fy : a.row * rh, by = b.fy >= 0 ? b.fy : b.row * rh;
            int rowA = Math.round(ay / rh), rowB = Math.round(by / rh);
            if (rowA != rowB) return rowA - rowB;
            float ax = a.fx >= 0 ? a.fx : a.col * cw, bx = b.fx >= 0 ? b.fx : b.col * cw;
            return Float.compare(ax, bx);
        });
        boolean[][] used = new boolean[rws][cols];
        for (HomeItem it : onPage) {
            it.fx = it.fy = -1f;
            boolean placed = false;
            for (int r = 0; r + it.rowSpan <= rws && !placed; r++) {
                for (int c = 0; c + it.colSpan <= cols && !placed; c++) {
                    boolean free = true;
                    for (int rr = r; rr < r + it.rowSpan && free; rr++)
                        for (int cc = c; cc < c + it.colSpan; cc++) if (used[rr][cc]) { free = false; break; }
                    if (!free) continue;
                    for (int rr = r; rr < r + it.rowSpan; rr++)
                        for (int cc = c; cc < c + it.colSpan; cc++) used[rr][cc] = true;
                    it.col = c;
                    it.row = r;
                    placed = true;
                }
            }
        }
        rebuildGridPages(gridPages.size(), g + 1);
        Toast.makeText(this, "Page tidied", Toast.LENGTH_SHORT).show();
    }

    private void resizeWidget(HomeGridLayout grid, HomeItem item, View child) {
        grid.startResize(child);
        Toast.makeText(this, R.string.resize_hint, Toast.LENGTH_SHORT).show();
    }

    private void showMovePagePicker(HomeGridLayout sourceGrid, HomeItem item, View child) {
        String[] labels = new String[gridPages.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(R.string.page_home) + " " + (i + 1);
        new AlertDialog.Builder(this)
                .setItems(labels, (dialog, which) -> {
                    if (which == item.page) return;
                    HomeGridLayout targetGrid = gridPages.get(which);
                    int[] free = findFreeArea(targetGrid, item.colSpan, item.rowSpan);
                    if (free == null) {
                        Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sourceGrid.removeView(child);
                    item.page = which;
                    item.col = free[0];
                    item.row = free[1];
                    layoutStore.saveAll(homeItems);
                    targetGrid.addView(child, targetGrid.gridParams(item.col, item.row, item.colSpan, item.rowSpan));
                })
                .show();
    }

    private void confirmRemoveHomeItem(HomeGridLayout grid, HomeItem item, View child) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_remove_home_item_title)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> removeHomeItem(grid, item, child))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void removeHomeItem(HomeGridLayout grid, HomeItem item, View child) {
        homeItems.remove(item);
        layoutStore.saveAll(homeItems);
        grid.removeView(child);
        if (item.type == HomeItem.TYPE_WIDGET) {
            widgetHostController.deleteAppWidgetId(item.appWidgetId);
        }
    }

    private AppInfo toAppInfo(HomeItem item) {
        return new AppInfo(item.packageName, item.activityName, item.label);
    }

    // ---------------------------------------------------------- shortcuts row

    /** Below the search bar, idle-only: quick-contact shortcuts. */
    private void refreshShortcutsRow(boolean idle) {
        dockContainer.removeAllViews();
        if (!idle) {
            dockScroll.setVisibility(View.GONE);
            return;
        }

        List<ShortcutContact> shortcuts = shortcutStore.loadAll();
        for (ShortcutContact contact : shortcuts) {
            View chip = LayoutInflater.from(this).inflate(R.layout.item_dock_shortcut, dockContainer, false);
            TextView avatar = chip.findViewById(R.id.avatar);
            TextView name = chip.findViewById(R.id.name);
            name.setText(contact.name);
            avatar.setText(initials(contact.name));

            View btnCall = chip.findViewById(R.id.btn_call);
            View btnSms = chip.findViewById(R.id.btn_sms);
            View btnWaMessage = chip.findViewById(R.id.btn_wa_message);
            View btnWaCall = chip.findViewById(R.id.btn_wa_call);

            btnCall.setVisibility(contact.has(ShortcutContact.ACTION_CALL) ? View.VISIBLE : View.GONE);
            btnSms.setVisibility(contact.has(ShortcutContact.ACTION_SMS) ? View.VISIBLE : View.GONE);
            btnWaMessage.setVisibility(contact.has(ShortcutContact.ACTION_WA_MESSAGE) ? View.VISIBLE : View.GONE);
            btnWaCall.setVisibility(contact.has(ShortcutContact.ACTION_WA_CALL) ? View.VISIBLE : View.GONE);

            btnCall.setOnClickListener(v -> doCall(contact.phoneNumber));
            btnSms.setOnClickListener(v -> IntentUtil.sendSms(this, contact.phoneNumber));
            btnWaMessage.setOnClickListener(v -> IntentUtil.whatsAppMessage(this, contact.phoneNumber));
            btnWaCall.setOnClickListener(v -> IntentUtil.whatsAppCall(this, contact.phoneNumber, false));

            dockContainer.addView(chip);
        }

        dockScroll.setVisibility(shortcuts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String initials(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    private void doCall(String number) {
        if (prefs.directCall()) {
            if (PermissionUtil.has(this, android.Manifest.permission.CALL_PHONE)) {
                IntentUtil.callDirect(this, number);
            } else {
                pendingDirectCallNumber = number;
                PermissionUtil.requestCallPhone(this);
            }
        } else {
            IntentUtil.dial(this, number);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtil.REQ_CALL_PHONE && pendingDirectCallNumber != null) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                IntentUtil.callDirect(this, pendingDirectCallNumber);
            } else {
                Toast.makeText(this, R.string.permission_call_denied, Toast.LENGTH_SHORT).show();
                IntentUtil.dial(this, pendingDirectCallNumber);
            }
            pendingDirectCallNumber = null;
        }
        if (requestCode == PermissionUtil.REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ContactIndexer.get(this).loadAsync();
            }
        }
    }

    // -------------------------------------------------------------- index

    /** Customised apps minus the ones the user hid from search. */
    private List<AppInfo> visibleApps() {
        java.util.Set<String> hidden = prefs.hiddenApps();
        List<AppInfo> all = com.piko.launcher.core.AppCustomizationStore.get(this).applyAll(rawApps);
        if (hidden.isEmpty()) return all;
        List<AppInfo> result = new ArrayList<>(all.size());
        for (AppInfo app : all) if (!hidden.contains(app.key())) result.add(app);
        return result;
    }

    @Override
    public void onIndexReady(List<AppInfo> apps) {
        rawApps = apps;
        currentApps = visibleApps();
        filterAndShow(searchInput.getText().toString());
    }

    @Override
    public void onContactsReady(List<ContactInfo> contacts) {
        currentContacts = contacts;
        filterAndShow(searchInput.getText().toString());
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.layoutRevision() != builtLayoutRevision) {
            recreate();
            return;
        }
        widgetHostController.startListening();
        currentApps = visibleApps();
        filterAndShow(searchInput.getText().toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        widgetHostController.stopListening();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (pager != null) pager.setCurrentItem(prefs.defaultPage(), false);
        if (searchInput != null) {
            searchInput.setText("");
            if (prefs.defaultPage() == Prefs.DEFAULT_PAGE_SEARCH) focusSearch();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && prefs.defaultPage() == Prefs.DEFAULT_PAGE_SEARCH
                && pager != null && pager.getCurrentItem() == Prefs.DEFAULT_PAGE_SEARCH) {
            focusSearch();
        }
    }

    private void focusSearch() {
        if (searchInput == null) return;
        searchInput.requestFocus();
        searchInput.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (overview) {
            exitOverview();
            return;
        }
        if (searchInput != null && searchInput.getText().length() > 0) {
            searchInput.setText("");
            return;
        }
        if (pager != null && pager.getCurrentItem() != Prefs.DEFAULT_PAGE_SEARCH) {
            pager.setCurrentItem(Prefs.DEFAULT_PAGE_SEARCH, true);
            return;
        }
        // Home is never "closed" by back — matches standard launcher behaviour.
    }
}
