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
    private LinearLayout pageIndicator;
    private EditText searchInput;
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
            PermissionUtil.requestContacts(this);
        }
    }

    // ---------------------------------------------------------------- pages

    private void buildPages() {
        FrameLayout pagerRoot = findViewById(R.id.pager_root);
        pageIndicator = findViewById(R.id.page_indicator);

        pager = new HorizontalPager(this);
        pagerRoot.addView(pager, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeItems = layoutStore.loadAll();

        View searchPage = buildSearchPage();
        pager.addView(searchPage);

        int pageCount = Math.max(1, prefs.gridPageCount());
        for (int i = 0; i < pageCount; i++) {
            HomeGridLayout grid = createGrid(i, prefs.columns(), prefs.rows());
            gridPages.add(grid);
            pager.addView(grid);
        }

        buildPageIndicator(1 + pageCount);
        pager.setOnPageChangeListener(index -> updatePageIndicator(index));

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

        searchWidgetGrid = createGrid(PAGE_SEARCH_WIDGETS, prefs.columns(), 2);
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
            } else if (result.type == SearchResult.TYPE_WEB) {
                IntentUtil.webSearch(this, primaryEngine(), result.text);
            } else if (result.type == SearchResult.TYPE_CALC && result.value != null) {
                IntentUtil.copyToClipboard(this, result.text, result.value);
            }
        });

        resultsList.setOnItemLongClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            SearchResult result = searchAdapter.getResultAt(position);
            if (result.type == SearchResult.TYPE_APP) {
                showAppOptionsMenu(v, result.app);
                return true;
            }
            return false;
        });

        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        return page;
    }

    /** Enter/search-action activates the top-ranked match — the one rendered nearest the search bar. */
    private void activateBestResult() {
        if (currentResults.isEmpty()) return;
        SearchResult best = currentResults.get(0);
        if (best.type == SearchResult.TYPE_APP) {
            launchApp(best.app);
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
        grid.setCallbacks(new HomeGridLayout.Callbacks() {
            @Override
            public void onEmptyCellLongPress(int col, int row) {
                showAddItemMenu(grid, pageId, col, row);
            }

            @Override
            public void onItemMoved(View child, int newCol, int newRow) {
                HomeItem item = (HomeItem) child.getTag();
                item.col = newCol;
                item.row = newRow;
                layoutStore.saveAll(homeItems);
            }

            @Override
            public void onItemRemoveRequested(View child) {
                HomeItem item = (HomeItem) child.getTag();
                removeHomeItem(grid, item, child);
            }

            @Override
            public void onItemLongPress(View child) {
                showHomeItemMenu(grid, child);
            }
        });
        populateGridPage(grid, pageId);
        return grid;
    }

    private void populateGridPage(HomeGridLayout grid, int pageIndex) {
        for (HomeItem item : homeItems) {
            if (item.page != pageIndex) continue;
            if (item.type == HomeItem.TYPE_APP) {
                View view = HomeIconViewFactory.createAppIcon(this, item);
                view.setOnClickListener(v -> IntentUtil.launchApp(this, toAppInfo(item)));
                grid.addView(view, grid.gridParams(item.col, item.row, 1, 1));
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
                grid.addView(hostView, grid.gridParams(item.col, item.row, item.colSpan, item.rowSpan));
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
        currentResults = searchEngine.searchAll(currentApps, currentContacts, query);

        if (!idle) {
            String trimmed = query.trim();
            if (com.piko.launcher.core.CurrencyConverter.looksLikeConversion(trimmed)) {
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
            } else if (com.piko.launcher.core.MathEval.looksLikeExpression(trimmed)) {
                Double value = com.piko.launcher.core.MathEval.evaluate(trimmed);
                if (value != null) {
                    currentResults.add(0, SearchResult.forCalc(trimmed, com.piko.launcher.core.MathEval.format(value)));
                }
            }
            if (currentResults.isEmpty() || (currentResults.size() == 1 && currentResults.get(0).type == SearchResult.TYPE_CALC)) {
                currentResults.add(SearchResult.forWeb(trimmed));
            }
        }

        renderResults(idle);
    }

    private void renderResults(boolean idle) {
        // Best match renders nearest the search bar: reverse so it lands last in a stackFromBottom list.
        List<SearchResult> display = new ArrayList<>(currentResults);
        Collections.reverse(display);
        searchAdapter.submit(display, searchInput.getText().toString());

        boolean hasResults = !currentResults.isEmpty();
        resultsList.setVisibility(!idle && hasResults ? View.VISIBLE : View.GONE);
        emptyText.setVisibility(!idle && !hasResults ? View.VISIBLE : View.GONE);
        idleWidgetArea.setVisibility(idle ? View.VISIBLE : View.GONE);
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

    private void showAppPickerDialog(int pageIndex, int col, int row) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.page_search, null, false);
        EditText input = dialogView.findViewById(R.id.search_input);
        ListView list = dialogView.findViewById(R.id.results_list);
        dialogView.findViewById(R.id.btn_settings).setVisibility(View.GONE);
        dialogView.findViewById(R.id.dock_scroll).setVisibility(View.GONE);

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

        list.setOnItemClickListener((parent, v, position, id) -> {
            AppInfo app = adapter.getAppAt(position);
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
        final int idMovePage = gridPages.size() > 1 ? ++id : -1;
        final int idRemove = ++id;

        if (idInfo != -1) menu.getMenu().add(0, idInfo, 0, R.string.menu_app_info);
        if (idEdit != -1) menu.getMenu().add(0, idEdit, 1, R.string.menu_edit_app);
        if (idUninstall != -1) menu.getMenu().add(0, idUninstall, 2, R.string.menu_uninstall);
        if (idResize != -1) menu.getMenu().add(0, idResize, 3, R.string.menu_resize_widget);
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
            } else if (clicked == idMovePage) {
                showMovePagePicker(grid, item, child);
            } else if (clicked == idRemove) {
                confirmRemoveHomeItem(grid, item, child);
            }
            return true;
        });
        menu.show();
    }

    private void resizeWidget(HomeGridLayout grid, HomeItem item, View child) {
        int newColSpan = (item.colSpan % grid.getColumns()) + 1;
        if (!grid.isAreaFree(item.col, item.row, newColSpan, item.rowSpan, child)) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        item.colSpan = newColSpan;
        grid.updateViewParams(child, grid.gridParams(item.col, item.row, item.colSpan, item.rowSpan));
        layoutStore.saveAll(homeItems);
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

    @Override
    public void onIndexReady(List<AppInfo> apps) {
        rawApps = apps;
        currentApps = com.piko.launcher.core.AppCustomizationStore.get(this).applyAll(rawApps);
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
        currentApps = com.piko.launcher.core.AppCustomizationStore.get(this).applyAll(rawApps);
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
        if (pager != null && pager.getCurrentItem() != Prefs.DEFAULT_PAGE_SEARCH) {
            pager.setCurrentItem(Prefs.DEFAULT_PAGE_SEARCH, true);
            return;
        }
        if (searchInput != null && searchInput.getText().length() > 0) {
            searchInput.setText("");
            return;
        }
        // Home is never "closed" by back — matches standard launcher behaviour.
    }
}
