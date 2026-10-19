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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.HomeLayoutStore;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.SearchEngine;
import com.piko.launcher.core.SearchLearningStore;
import com.piko.launcher.core.SearchResult;
import com.piko.launcher.core.UsageStore;
import com.piko.launcher.ui.AppListAdapter;
import com.piko.launcher.ui.HorizontalPager;
import com.piko.launcher.ui.SearchResultAdapter;
import com.piko.launcher.ui.home.HomeGridLayout;
import com.piko.launcher.ui.home.HomeIconViewFactory;
import com.piko.launcher.ui.settings.SettingsActivity;
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

    private Prefs prefs;
    private UsageStore usageStore;
    private HomeLayoutStore layoutStore;
    private SearchEngine searchEngine;
    private WidgetHostController widgetHostController;

    private HorizontalPager pager;
    private FrameLayout pagerRoot;
    private LinearLayout overviewBar;
    private TextView overviewTitle;
    private LinearLayout ovRow;
    private View ovTidy, ovDefault, ovDelete, ovWidget, ovAddPage, ovAddLeft, ovPrev, ovNext;
    private boolean overview;
    private LinearLayout pageIndicator;
    private EditText searchInput;
    private View resultsOverlay;
    private ListView resultsList;
    private TextView emptyText;
    private FrameLayout idleWidgetArea;
    private SearchResultAdapter searchAdapter;

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

        buildSearchPage();

        int pageCount = Math.max(1, prefs.gridPageCount());
        for (int i = 0; i < pageCount; i++) {
            HomeGridLayout grid = createGrid(i, prefs.columns(), prefs.rows());
            gridPages.add(grid);
            pager.addView(grid);
        }

        buildPageIndicator(pageCount);
        pager.setOnPageChangeListener(index -> {
            updatePageIndicator(index);
            updateOverviewTitle();
        });
        buildOverviewBar();
        final View rootView = findViewById(R.id.root);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this::syncKeyboardLayout);

        pager.post(() -> pager.setCurrentItem(defaultIndex(), false));
        if (!prefs.isFirstRunDone()) startActivity(new Intent(this, com.piko.launcher.ui.settings.SetupActivity.class));
        pager.post(this::updateIndicatorInitial);
    }

    private View buildSearchPage() {
        View page = LayoutInflater.from(this).inflate(R.layout.page_search, null, false);
        searchInput = page.findViewById(R.id.search_input);
        resultsList = page.findViewById(R.id.results_list);
        emptyText = page.findViewById(R.id.empty_text);
        idleWidgetArea = page.findViewById(R.id.idle_widget_area);
        ImageButton settingsButton = page.findViewById(R.id.btn_settings);

        // The search bar is shared by every page, so it lives outside the pager.
        View searchBar = page.findViewById(R.id.search_bar);
        ((ViewGroup) searchBar.getParent()).removeView(searchBar);
        ((ViewGroup) findViewById(R.id.search_bar_container)).addView(searchBar);

        // Results are an overlay above the pager, shown only while typing; page 0 is a normal grid page.
        resultsOverlay = page.findViewById(R.id.results_overlay);
        ((ViewGroup) resultsOverlay.getParent()).removeView(resultsOverlay);
        resultsOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((FrameLayout) findViewById(R.id.root)).addView(resultsOverlay, 1, overlayLp);


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
                copyCalc(result);
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
            if (result.type == SearchResult.TYPE_ACTION && result.copyText != null) {
                IntentUtil.copyToClipboard(this, "copy", result.copyText);
                return true;
            }
            if (result.type == SearchResult.TYPE_ACTION && result.shortcutKind != null) {
                PopupMenu menu = new PopupMenu(this, v);
                menu.getMenu().add(0, 1, 0, R.string.menu_add_to_home);
                menu.setOnMenuItemClickListener(m -> {
                    HomeItem item = new HomeItem();
                    item.type = HomeItem.TYPE_SHORTCUT;
                    item.kind = result.shortcutKind;
                    item.data = result.shortcutData;
                    item.label = result.text;
                    placeOnHome(item);
                    return true;
                });
                menu.show();
                return true;
            }
            return false;
        });

        searchInput.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            int n = searchAdapter.getCount();
            if (n == 0) return false;
            int sel = searchAdapter.getSelected() < 0 ? n - 1 : searchAdapter.getSelected();
            if (code == android.view.KeyEvent.KEYCODE_DPAD_UP) sel = Math.max(0, sel - 1);
            else if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) sel = Math.min(n - 1, sel + 1);
            else return false;
            searchAdapter.setSelected(sel);
            resultsList.smoothScrollToPosition(sel);
            return true;
        });

        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        return page;
    }

    /** Enter/search-action activates the top-ranked match — the one rendered nearest the search bar. */
    private void activateBestResult() {
        if (currentResults.isEmpty()) return;
        int sel = searchAdapter.getSelected();
        SearchResult best = sel >= 0 && sel < searchAdapter.getCount() ? searchAdapter.getResultAt(sel) : currentResults.get(0);
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
        else if (best.type == SearchResult.TYPE_CALC && best.value != null) copyCalc(best);
    }

    private void copyCalc(SearchResult r) {
        IntentUtil.copyToClipboard(this, r.text, r.value);
        if (r.text != null && !r.text.isEmpty()) prefs.addCalcHistory(r.text, r.value);
    }

    /** The first enabled engine, used when the user taps the web-search row itself rather than an engine chip. */
    private String primaryEngine() {
        List<String> enabled = prefs.enabledSearchEngines();
        return enabled.isEmpty() ? "google" : enabled.get(0);
    }

    private HomeGridLayout createGrid(int pageId, int columns, int rows) {
        HomeGridLayout grid = new HomeGridLayout(this, columns, rows);
        grid.setFreeform(prefs.freeform());
        grid.setLongPressEnabled(!overview);
        grid.setCallbacks(new HomeGridLayout.Callbacks() {
            @Override
            public void onEmptyCellLongPress(int col, int row) {
                if (overview) showAddItemMenu(grid, pageId, col, row);
                else enterOverview();
            }

            @Override
            public int[] actionsFor(View child) {
                HomeItem item = (HomeItem) child.getTag();
                if (item.type == HomeItem.TYPE_APP) {
                    return new int[]{HomeGridLayout.ACTION_REMOVE, HomeGridLayout.ACTION_UNINSTALL, HomeGridLayout.ACTION_INFO,
                            HomeGridLayout.ACTION_EDIT, HomeGridLayout.ACTION_NAME};
                }
                return item.type == HomeItem.TYPE_WIDGET
                        ? new int[]{HomeGridLayout.ACTION_REMOVE, HomeGridLayout.ACTION_RESIZE}
                        : new int[]{HomeGridLayout.ACTION_REMOVE};
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
                item.col = lp.col;
                item.row = lp.row;
                item.fx = lp.fx;
                item.fy = lp.fy;
                item.fw = lp.fw;
                item.fh = lp.fh;
                if (child instanceof android.appwidget.AppWidgetHostView) {
                    float d = getResources().getDisplayMetrics().density;
                    int w = Math.round(child.getWidth() / d), h = Math.round(child.getHeight() / d);
                    ((android.appwidget.AppWidgetHostView) child).updateAppWidgetSize(null, w, h, w, h);
                }
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
        lp.fw = item.fw;
        lp.fh = item.fh;
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
            if (item.type != HomeItem.TYPE_WIDGET) {
                grid.addView(viewForItem(item), paramsFor(grid, item));
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

    private int maxRootHeight;
    private boolean keyboardUp;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        maxRootHeight = 0;
        if (searchAdapter != null) searchAdapter.setShowSelection(keyboardUp || hasHardwareKeyboard());
    }

    private boolean hasHardwareKeyboard() {
        android.content.res.Configuration c = getResources().getConfiguration();
        return c.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
                && c.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO;
    }

    /**
     * The window still resizes for the keyboard, but the page area keeps its full size and the results overlay
     * stops above the bottom group, so nothing on the pages is squeezed or re-laid out.
     */
    private void syncKeyboardLayout() {
        View root = findViewById(R.id.root);
        View bottom = findViewById(R.id.bottom_group);
        int h = root.getHeight();
        if (h <= 0 || bottom == null) return;
        maxRootHeight = Math.max(maxRootHeight, h);
        int full = maxRootHeight;
        android.view.WindowInsets wi = root.getRootWindowInsets();
        if (wi != null) {
            android.graphics.Point real = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getRealSize(real);
            int[] loc = new int[2];
            root.getLocationOnScreen(loc);
            full = Math.max(full, real.y - loc[1] - wi.getStableInsetBottom());
        }
        ViewGroup.LayoutParams plp = pagerRoot.getLayoutParams();
        if (plp.height != full) {
            plp.height = full;
            pagerRoot.setLayoutParams(plp);
        }
        FrameLayout.LayoutParams olp = (FrameLayout.LayoutParams) resultsOverlay.getLayoutParams();
        if (olp.bottomMargin != bottom.getHeight()) {
            olp.bottomMargin = bottom.getHeight();
            resultsOverlay.setLayoutParams(olp);
        }
        boolean up = h < full - dp(120);
        if (up != keyboardUp) {
            keyboardUp = up;
            searchAdapter.setShowSelection(up || hasHardwareKeyboard());
            if (!up && !hasHardwareKeyboard() && searchInput.hasFocus()) searchInput.clearFocus();
        }
    }

    private void buildPageIndicator(int totalPages) {
        pageIndicator.removeAllViews();
        pageIndicator.setVisibility(totalPages > 1 && resultsOverlay.getVisibility() != View.VISIBLE ? View.VISIBLE : View.INVISIBLE);
        float d = getResources().getDisplayMetrics().density;
        int dotSize = (int) (7 * d);
        int margin = (int) (4 * d);
        int home = defaultIndex();
        for (int i = 0; i < totalPages; i++) {
            View dot;
            LinearLayout.LayoutParams lp;
            if (i == home) {
                android.widget.ImageView h = new android.widget.ImageView(this);
                h.setImageResource(R.drawable.ic_home);
                dot = h;
                lp = new LinearLayout.LayoutParams((int) (14 * d), (int) (14 * d));
            } else {
                dot = new View(this);
                dot.setBackgroundResource(R.drawable.bg_dot_indicator);
                lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            }
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            pageIndicator.addView(dot);
        }
    }

    private void updateIndicatorInitial() {
        updatePageIndicator(pager.getCurrentItem());
    }

    private void updatePageIndicator(int index) {
        int home = defaultIndex();
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            View c = pageIndicator.getChildAt(i);
            if ((i == home) != (c instanceof android.widget.ImageView)) {
                buildPageIndicator(pageIndicator.getChildCount());
                updatePageIndicator(index);
                return;
            }
            if (c instanceof android.widget.ImageView) {
                ((android.widget.ImageView) c).setColorFilter(i == index ? 0xFF7C9CFF : 0xFF6B7080);
            } else {
                c.setBackgroundResource(i == index ? R.drawable.bg_dot_indicator_active : R.drawable.bg_dot_indicator);
            }
        }
    }

    // -------------------------------------------------------------- search

    private String lastQuery = "";

    private void filterAndShow(String query) {
        if (!query.equals(lastQuery)) expandedContacts.clear();
        lastQuery = query;
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
            if (trimmed.equalsIgnoreCase("history") || trimmed.equalsIgnoreCase("calc history")) {
                List<String[]> hist = prefs.calcHistory();
                int at = 0;
                for (String[] h : hist) currentResults.add(at++, SearchResult.forCalc(h[0], h[1]));
                if (!hist.isEmpty()) {
                    currentResults.add(at, SearchResult.forAction(null, "Clear calculation history", "Piko", () -> {
                        prefs.clearCalcHistory();
                        searchInput.setText("");
                    }).withIcon(R.drawable.ic_trash));
                } else {
                    currentResults.add(at, SearchResult.forAction(null, "No calculations yet", "Tap a result to save it", () -> { })
                            .withIcon(R.drawable.ic_calc));
                }
            }
            if (prefs.flag(Prefs.FLAG_SEARCH_CONTACTS) && trimmed.matches("\\+?[0-9][0-9\\s\\-().]*")
                    && trimmed.replaceAll("[^0-9]", "").length() >= 3 && !hasContactWithNumber(trimmed)) {
                // Not in the address book: still offer call/message for the typed number.
                int at = !currentResults.isEmpty() && currentResults.get(0).type == SearchResult.TYPE_CALC ? 1 : 0;
                currentResults.add(at, SearchResult.forContact(new ContactInfo(-1, trimmed, trimmed)));
            }
            if (prefs.flag(Prefs.FLAG_SEARCH_CONTACTS) && android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                int at = !currentResults.isEmpty() && currentResults.get(0).type == SearchResult.TYPE_CALC ? 1 : 0;
                currentResults.addAll(at, emailRows(trimmed));
            }
            if (prefs.flag(Prefs.FLAG_SEARCH_WEB)) {
                List<SearchResult.Chip> chips = new ArrayList<>();
                for (String engineId : prefs.enabledSearchEngines()) {
                    com.piko.launcher.core.WebSearchEngines.Engine e = prefs.engineById(engineId);
                    SearchResult.Chip c = new SearchResult.Chip();
                    c.badge = e.label.substring(0, 1);
                    c.label = e.label;
                    c.color = chipColor("web");
                    c.run = () -> IntentUtil.webSearch(this, engineId, trimmed);
                    c.onLongPress = v -> {
                        SearchResult pin = SearchResult.forAction(null, getString(R.string.search_engine_for, e.label),
                                trimmed, null).pinnable("web", engineId);
                        showAddToHome(v, pin);
                    };
                    chips.add(c);
                }
                // A handful of engines fit in one strip; more wrap onto extra strips.
                for (int i = 0; i < chips.size(); i += 5) {
                    currentResults.add(SearchResult.forChips(new ArrayList<>(chips.subList(i, Math.min(chips.size(), i + 5)))));
                }
            }
        }
        currentResults = explodeContacts(currentResults, query);
        if (!idle && prefs.flag(Prefs.FLAG_QUICK_ACTIONS)) addQuickActions(query.trim());

        renderResults(idle);
    }

    private static int chipColor(String kind) {
        switch (kind == null ? "" : kind) {
            case "call": return 0xFF2E9F64;
            case "sms": return 0xFF4A7BE0;
            case "wa": return 0xFF1FA855;
            case "wacall": return 0xFF138A7A;
            case "contact": return 0xFF6B7690;
            case "copy": case "copyemail": return 0xFFC98A1E;
            case "email": return 0xFFD2574B;
            case "addcontact": case "addemail": return 0xFF8A5CD6;
            case "web": return 0xFF5B6FD6;
            default: return 0xFF6B7690;
        }
    }

    private static String chipText(SearchResult k) {
        if (k.chipLabel != null) return k.chipLabel;
        switch (k.shortcutKind == null ? "" : k.shortcutKind) {
            case "call": return "Call";
            case "sms": return "SMS";
            case "wa": return "WhatsApp";
            case "wacall": return "WA call";
            case "contact": return "Open";
            case "addcontact": case "addemail": return "Add";
            case "copy": case "copyemail": return "Copy";
            case "email": return "Write";
            default: return k.text;
        }
    }

    /** A header row that toggles a strip of compact action buttons shown right after it. */
    private List<SearchResult> group(String key, String title, String sub, int icon, String copy, List<SearchResult> kids) {
        return group(key, title, sub, icon, copy, kids, false);
    }

    /** {@code fixed}: nothing to collapse (a lone result), so the buttons are always shown and the header is a plain label. */
    private List<SearchResult> group(String key, String title, String sub, int icon, String copy, List<SearchResult> kids, boolean fixed) {
        boolean open = fixed || expandedContacts.contains(key);
        List<SearchResult> rows = new ArrayList<>();
        rows.add(SearchResult.forAction(null, title, sub, () -> {
            if (fixed) return;
            if (!expandedContacts.remove(key)) expandedContacts.add(key);
            filterAndShow(searchInput.getText().toString());
        }).withIcon(icon).grouped(fixed ? SearchResult.GROUP_FIXED : open ? SearchResult.GROUP_OPEN : SearchResult.GROUP_HEADER).copyOnLongPress(copy));
        if (open) {
            List<SearchResult.Chip> chips = new ArrayList<>();
            for (SearchResult k : kids) {
                SearchResult.Chip c = new SearchResult.Chip();
                c.icon = k.icon;
                c.badge = k.badge;
                c.label = chipText(k);
                c.color = chipColor(k.shortcutKind);
                c.run = k.action;
                if (k.shortcutKind != null) c.onLongPress = v -> showAddToHome(v, k);
                chips.add(c);
            }
            rows.add(SearchResult.forChips(chips));
        }
        return rows;
    }

    private void showAddToHome(View anchor, SearchResult result) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_to_home);
        menu.setOnMenuItemClickListener(m -> {
            HomeItem item = new HomeItem();
            item.type = HomeItem.TYPE_SHORTCUT;
            item.kind = result.shortcutKind;
            item.data = result.shortcutData;
            item.label = result.text;
            placeOnHome(item);
            return true;
        });
        menu.show();
    }

    private final java.util.Set<String> expandedContacts = new java.util.HashSet<>();

    /** Shows each contact as one row; tapping it expands one row per enabled action (call, SMS, WhatsApp...), keeping ranking order. */
    private List<SearchResult> explodeContacts(List<SearchResult> in, String query) {
        List<SearchResult> out = new ArrayList<>();
        int contactCount = 0;
        for (SearchResult r : in) if (r.type == SearchResult.TYPE_CONTACT) contactCount++;
        for (SearchResult r : in) {
            if (r.type != SearchResult.TYPE_CONTACT) {
                out.add(r);
                continue;
            }
            final ContactInfo c = r.contact;
            final String who = c.name.equals(c.phoneNumber) ? c.phoneNumber : c.name;
            final String sub = c.name.equals(c.phoneNumber) ? "" : c.phoneNumber;
            final String q = query;
            List<SearchResult> kids = new ArrayList<>();
            List<SearchResult> out0 = out;
            out = kids;
            if (prefs.flag(Prefs.FLAG_ACTION_CALL)) out.add(contactAction("call", c.phoneNumber, getString(R.string.act_call, who), sub, c, q,
                    () -> doCall(c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_SMS)) out.add(contactAction("sms", c.phoneNumber, getString(R.string.act_sms, who), sub, c, q,
                    () -> IntentUtil.sendSms(this, c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_WA_MESSAGE)) out.add(contactAction("wa", c.phoneNumber, getString(R.string.act_wa_message, who), sub, c, q,
                    () -> IntentUtil.whatsAppMessage(this, c.phoneNumber)));
            if (prefs.flag(Prefs.FLAG_ACTION_WA_CALL)) out.add(contactAction("wacall", c.phoneNumber, getString(R.string.act_wa_call, who), sub, c, q,
                    () -> IntentUtil.whatsAppCall(this, c.phoneNumber, false)));
            if (c.contactId >= 0) out.add(contactAction("contact", String.valueOf(c.contactId), getString(R.string.act_open_contact, who), sub, c, q,
                    () -> IntentUtil.openContactInfo(this, c.contactId)));
            else out.add(contactAction("addcontact", c.phoneNumber, getString(R.string.act_add_contact), c.phoneNumber, c, q,
                    () -> addToContacts(c.phoneNumber)));
            out.add(contactAction("copy", c.phoneNumber, getString(R.string.act_copy_number, who), sub, c, q,
                    () -> IntentUtil.copyToClipboard(this, "number", c.phoneNumber)));
            out = out0;
            out.addAll(group("c:" + SearchEngine.contactKey(c), who, sub, R.drawable.ic_person, c.phoneNumber, kids, contactCount == 1));
        }
        return out;
    }

    private List<SearchResult> emailRows(String email) {
        List<SearchResult> kids = new ArrayList<>();
        kids.add(SearchResult.forAction(null, getString(R.string.act_write_email, email), "", () -> writeEmail(email))
                .withIcon(HomeIconViewFactory.iconForKind("email")).pinnable("email", email));
        kids.add(SearchResult.forAction(null, getString(R.string.act_add_contact), email, () -> addEmailToContacts(email))
                .withIcon(HomeIconViewFactory.iconForKind("addemail")).pinnable("addemail", email));
        kids.add(SearchResult.forAction(null, getString(R.string.act_copy_email), email,
                () -> IntentUtil.copyToClipboard(this, "email", email))
                .withIcon(HomeIconViewFactory.iconForKind("copyemail")).pinnable("copyemail", email));
        return group("e:" + email, email, "", R.drawable.ic_mail, email, kids, true);
    }

    private void writeEmail(String email) {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + email)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void addEmailToContacts(String email) {
        Intent intent = new Intent(android.provider.ContactsContract.Intents.Insert.ACTION);
        intent.setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE);
        intent.putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, email);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void addToContacts(String number) {
        Intent intent = new Intent(android.provider.ContactsContract.Intents.Insert.ACTION);
        intent.setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE);
        intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private SearchResult contactAction(String kind, String data, String title, String sub, ContactInfo c, String query, Runnable run) {
        return SearchResult.forAction(null, title, sub, () -> {
            run.run();
            if (c.contactId >= 0) searchEngine.recordChoice(query, SearchEngine.contactKey(c));
        }).withIcon(HomeIconViewFactory.iconForKind(kind)).pinnable(kind, data);
    }

    // ---------------------------------------------------- piko quick actions

    private static final String[][] PIKO_ACTIONS = {
            {"history", "Calculation history", "calc calculator math history recent results"},
            {"settings", "Piko settings", "settings preferences options config"},
            {"overview", "Edit home screen", "edit pages overview rearrange zoom organize"},
            {"addpage", "Add page", "new page add"},
            {"setdefault", "Make this page the default", "default home page start"},
            {"hidenames", "Show or hide app names", "hide show names labels toggle"},
            {"freeform", "Free-form or grid placement", "freeform grid snap placement toggle"},
            {"iconpack", "Icon pack", "icon pack theme"},
            {"hidden", "Hidden apps", "hidden apps hide"},
            {"permissions", "Permissions", "permissions contacts access"},
            {"setup", "Run setup again", "setup welcome first run"},
    };

    private void addQuickActions(String query) {
        String q = query.toLowerCase(java.util.Locale.ROOT);
        if (q.length() < 2) return;
        String[] tokens = q.split("\\s+");
        int at = 0;
        for (int i = 0; i < currentResults.size(); i++) {
            if (currentResults.get(i).type == SearchResult.TYPE_APP) at = i + 1;
        }
        for (String[] a : PIKO_ACTIONS) {
            String hay = " " + a[1].toLowerCase(java.util.Locale.ROOT) + " " + a[2];
            boolean all = true;
            for (String t : tokens) if (!hay.contains(" " + t)) all = false;
            if (!all) continue;
            final String id = a[0];
            currentResults.add(at++, SearchResult.forAction(null, a[1], "Piko", () -> runPikoAction(id))
                    .withIcon(R.drawable.ic_bolt).pinnable("piko", id));
        }
    }

    private void runPikoAction(String id) {
        if (id.equals("history")) {
            searchInput.setText("history");
            searchInput.setSelection(searchInput.getText().length());
            return;
        }
        searchInput.setText("");
        hideKeyboard();
        switch (id) {
            case "settings": startActivity(new Intent(this, SettingsActivity.class)); break;
            case "overview": enterOverview(); break;
            case "addpage": addPage(gridPages.size()); break;
            case "setdefault":
                prefs.setDefaultPage(pager.getCurrentItem());
                Toast.makeText(this, R.string.ov_default_set, Toast.LENGTH_SHORT).show();
                break;
            case "hidenames":
                prefs.setHideAllLabels(!prefs.hideAllLabels());
                prefs.bumpLayoutRevision();
                recreate();
                break;
            case "freeform":
                prefs.setFreeform(!prefs.freeform());
                prefs.bumpLayoutRevision();
                recreate();
                break;
            case "iconpack": startActivity(new Intent(this, com.piko.launcher.ui.settings.IconPackActivity.class)); break;
            case "hidden": startActivity(new Intent(this, com.piko.launcher.ui.settings.HiddenAppsActivity.class)); break;
            case "permissions": startActivity(new Intent(this, com.piko.launcher.ui.settings.PermissionsActivity.class)); break;
            case "setup": startActivity(new Intent(this, com.piko.launcher.ui.settings.SetupActivity.class)); break;
        }
    }

    /** Executes a pinned shortcut from the home screen. */
    private void runShortcut(String kind, String data) {
        switch (kind) {
            case "call": doCall(data); break;
            case "sms": IntentUtil.sendSms(this, data); break;
            case "wa": IntentUtil.whatsAppMessage(this, data); break;
            case "wacall": IntentUtil.whatsAppCall(this, data, false); break;
            case "contact":
                try {
                    IntentUtil.openContactInfo(this, Long.parseLong(data));
                } catch (NumberFormatException ignored) {
                }
                break;
            case "copy": IntentUtil.copyToClipboard(this, "number", data); break;
            case "addcontact": addToContacts(data); break;
            case "email": writeEmail(data); break;
            case "addemail": addEmailToContacts(data); break;
            case "copyemail": IntentUtil.copyToClipboard(this, "email", data); break;
            case "web": IntentUtil.webSearch(this, data, ""); break;
            case "piko": runPikoAction(data); break;
        }
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
        if (!idle && !display.isEmpty()) searchAdapter.setSelected(display.size() - 1);

        boolean hasResults = !currentResults.isEmpty();
        resultsOverlay.setVisibility(idle ? View.GONE : View.VISIBLE);
        pageIndicator.setVisibility(idle && gridPages.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        resultsList.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        emptyText.setVisibility(!hasResults ? View.VISIBLE : View.GONE);
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
        if (!android.appwidget.AppWidgetManager.getInstance(this)
                .getInstalledProvidersForPackage(app.packageName, null).isEmpty()) {
            menu.getMenu().add(0, 5, 1, R.string.menu_app_widgets);
        }
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 5:
                    startWidgetPicker(pager.getCurrentItem(), 0, 0, app.packageName);
                    return true;
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
        return gridPages.get(pageId);
    }

    private void showAddItemMenu(HomeGridLayout grid, int pageIndex, int col, int row) {
        PopupMenu menu = new PopupMenu(this, grid);
        menu.getMenu().add(0, 1, 0, R.string.menu_add_app);
        menu.getMenu().add(0, 2, 1, R.string.menu_add_widget);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showAppPickerDialog(pageIndex, col, row);
            } else {
                startWidgetPicker(pageIndex, col, row, null);
            }
            return true;
        });
        menu.show();
    }

    private void startWidgetPicker(int pageIndex, int col, int row, String onlyPackage) {
        pendingWidgetPage = pageIndex;
        pendingWidgetCol = col;
        pendingWidgetRow = row;
        Intent intent = new Intent(this, WidgetPickerActivity.class);
        if (onlyPackage != null) intent.putExtra(WidgetPickerActivity.EXTRA_PACKAGE, onlyPackage);
        startActivityForResult(intent, REQ_PICK_WIDGET_PROVIDER);
    }

    private void addAppToFirstFreeCell(AppInfo app) {
        HomeItem item = new HomeItem();
        item.type = HomeItem.TYPE_APP;
        item.packageName = app.packageName;
        item.activityName = app.activityName;
        item.label = app.label;
        placeOnHome(item);
    }

    /** Puts a new item in the first free cell, preferring the page the user is looking at. */
    private void placeOnHome(HomeItem item) {
        int current = pager.getCurrentItem();
        for (int n = 0; n < gridPages.size(); n++) {
            int p = (current + n) % gridPages.size();
            HomeGridLayout grid = gridPages.get(p);
            int[] cell = findFreeArea(grid, 1, 1);
            if (cell == null) continue;
            item.page = p;
            item.col = cell[0];
            item.row = cell[1];
            homeItems.add(item);
            layoutStore.saveAll(homeItems);
            grid.addView(viewForItem(item), grid.gridParams(cell[0], cell[1], 1, 1));
            Toast.makeText(this, R.string.menu_add_to_home, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
    }

    /** Builds and wires the home view for an app, shortcut or folder item. */
    private View viewForItem(HomeItem item) {
        View view;
        switch (item.type) {
            case HomeItem.TYPE_SHORTCUT:
                view = HomeIconViewFactory.createShortcut(this, item);
                view.setOnClickListener(v -> runShortcut(item.kind, item.data));
                break;
            case HomeItem.TYPE_FOLDER:
                view = HomeIconViewFactory.createFolder(this, item);
                view.setOnClickListener(v -> openFolder(item, view));
                break;
            default:
                view = HomeIconViewFactory.createAppIcon(this, item);
                view.setOnClickListener(v -> IntentUtil.launchApp(this, toAppInfo(item)));
        }
        return view;
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

    // ------------------------------------------------------------- folders

    private AppInfo appForKey(String key) {
        for (AppInfo a : rawApps) if (a.key().equals(key)) return a;
        int slash = key.indexOf('/');
        return slash < 0 ? null : new AppInfo(key.substring(0, slash), key.substring(slash + 1), key.substring(0, slash));
    }

    private void renameItem(HomeItem item, View child) {
        EditText input = new EditText(this);
        input.setText(item.label);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_rename)
                .setView(input)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    item.label = name;
                    layoutStore.saveAll(homeItems);
                    TextView label = child.findViewById(R.id.label);
                    if (label != null) label.setText(name);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Pick an existing folder (or a new one) to move this app into. */
    private void chooseFolder(HomeGridLayout grid, HomeItem app, View child) {
        List<HomeItem> folders = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (HomeItem it : homeItems) {
            if (it.type == HomeItem.TYPE_FOLDER) {
                folders.add(it);
                names.add(it.label);
            }
        }
        names.add(getString(R.string.new_folder));
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_add_to_folder)
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    String key = app.packageName + "/" + app.activityName;
                    if (which < folders.size()) {
                        folders.get(which).children.add(key);
                        removeHomeItem(grid, app, child);
                        layoutStore.saveAll(homeItems);
                        return;
                    }
                    HomeItem folder = new HomeItem();
                    folder.type = HomeItem.TYPE_FOLDER;
                    folder.label = getString(R.string.folder_default_name);
                    folder.page = app.page;
                    folder.col = app.col;
                    folder.row = app.row;
                    folder.fx = app.fx;
                    folder.fy = app.fy;
                    folder.children.add(key);
                    removeHomeItem(grid, app, child);
                    homeItems.add(folder);
                    layoutStore.saveAll(homeItems);
                    View fv = viewForItem(folder);
                    grid.addView(fv, paramsFor(grid, folder));
                    renameItem(folder, fv);
                })
                .show();
    }

    private void openFolder(HomeItem folder, View anchor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(8));
        android.widget.GridLayout gridView = new android.widget.GridLayout(this);
        gridView.setColumnCount(4);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(folder.label).setView(box).create();
        int size = dp(36);
        for (String key : new ArrayList<>(folder.children)) {
            AppInfo app = appForKey(key);
            if (app == null) continue;
            View cell = LayoutInflater.from(this).inflate(R.layout.view_home_icon, null, false);
            IconCache.get(this).load(app, (ImageView) cell.findViewById(R.id.icon), size);
            ((TextView) cell.findViewById(R.id.label)).setText(app.label);
            ((TextView) cell.findViewById(R.id.label)).setMaxWidth(dp(64));
            cell.setBackgroundResource(R.drawable.bg_pressable);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            cell.setOnClickListener(v -> {
                dialog.dismiss();
                IntentUtil.launchApp(this, app);
            });
            cell.setOnLongClickListener(v -> {
                PopupMenu menu = new PopupMenu(this, v);
                menu.getMenu().add(0, 1, 0, R.string.menu_remove_from_folder);
                menu.getMenu().add(0, 2, 1, R.string.menu_app_info);
                menu.setOnMenuItemClickListener(m -> {
                    if (m.getItemId() == 1) {
                        folder.children.remove(key);
                        layoutStore.saveAll(homeItems);
                        dialog.dismiss();
                        HomeItem item = new HomeItem();
                        item.type = HomeItem.TYPE_APP;
                        item.packageName = app.packageName;
                        item.activityName = app.activityName;
                        item.label = app.label;
                        placeOnHome(item);
                    } else {
                        IntentUtil.openAppInfo(this, app.packageName);
                    }
                    return true;
                });
                menu.show();
                return true;
            });
            gridView.addView(cell);
        }
        box.addView(gridView);
        TextView add = new TextView(this);
        add.setText(R.string.folder_add_app);
        add.setTextColor(getResources().getColor(R.color.piko_accent));
        add.setPadding(dp(4), dp(14), dp(4), dp(6));
        add.setOnClickListener(v -> {
            dialog.dismiss();
            showAppChooser((app, view, picker) -> {
                String key = app.packageName + "/" + app.activityName;
                if (!folder.children.contains(key)) folder.children.add(key);
                layoutStore.saveAll(homeItems);
                picker.dismiss();
                openFolder(folder, anchor);
            });
        });
        box.addView(add);
        dialog.show();
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
        final int idFolder = item.type == HomeItem.TYPE_APP ? ++id : -1;
        final int idRename = item.type == HomeItem.TYPE_FOLDER || item.type == HomeItem.TYPE_SHORTCUT ? ++id : -1;
        final int idRemove = ++id;

        if (idInfo != -1) menu.getMenu().add(0, idInfo, 0, R.string.menu_app_info);
        if (idEdit != -1) menu.getMenu().add(0, idEdit, 1, R.string.menu_edit_app);
        if (idUninstall != -1) menu.getMenu().add(0, idUninstall, 2, R.string.menu_uninstall);
        if (idResize != -1) menu.getMenu().add(0, idResize, 3, R.string.menu_resize_widget);
        if (idName != -1) menu.getMenu().add(0, idName, 3, com.piko.launcher.core.AppCustomizationStore.get(this)
                .hidesLabel(item.packageName + "/" + item.activityName) ? R.string.menu_show_name : R.string.menu_hide_name);
        if (idMovePage != -1) menu.getMenu().add(0, idMovePage, 4, R.string.menu_add_to_home);
        if (idFolder != -1) menu.getMenu().add(0, idFolder, 4, R.string.menu_add_to_folder);
        if (idRename != -1) menu.getMenu().add(0, idRename, 1, R.string.menu_rename);
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
            } else if (clicked == idFolder) {
                chooseFolder(grid, item, child);
            } else if (clicked == idRename) {
                renameItem(item, child);
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
        int targetIdx = item.page + direction;
        if (targetIdx < 0) return null;
        if (targetIdx >= gridPages.size()) {
            // Dragged past the last page: grow a fresh one on the fly (up to a sane limit).
            if (gridPages.size() >= 8) return null;
            HomeGridLayout fresh = createGrid(gridPages.size(), prefs.columns(), prefs.rows());
            gridPages.add(fresh);
            pager.addView(fresh);
            prefs.setGridPageCount(gridPages.size());
            builtLayoutRevision = prefs.layoutRevision();
            buildPageIndicator(gridPages.size());
            updatePageIndicator(pager.getCurrentItem());
        }
        int targetPage = targetIdx;
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

        ovRow = new LinearLayout(this);
        ovRow.setOrientation(LinearLayout.HORIZONTAL);
        ovTidy = ovButton(R.drawable.ic_grid, R.string.ov_tidy, v -> tidyPage());
        ovDefault = ovButton(R.drawable.ic_home, R.string.ov_set_default, v -> {
            prefs.setDefaultPage(pager.getCurrentItem());
            updateOverviewTitle();
            Toast.makeText(this, R.string.ov_default_set, Toast.LENGTH_SHORT).show();
        });
        ovDelete = ovButton(R.drawable.ic_trash, R.string.ov_delete_page, v -> deletePage());
        ovWidget = ovButton(R.drawable.ic_add_circle, R.string.ov_add_item, v -> {
            startWidgetPicker(pager.getCurrentItem(), 0, 0, null);
        });
        ovRow.addView(ovTidy);
        ovRow.addView(ovDefault);
        ovRow.addView(ovDelete);
        ovRow.addView(ovWidget);
        overviewBar.addView(ovRow);

        FrameLayout root = (FrameLayout) findViewById(R.id.root);

        ovPrev = edgeHint(R.drawable.ic_left, android.view.Gravity.START, 0, v -> pager.setCurrentItem(pager.getCurrentItem() - 1, true));
        ovNext = edgeHint(R.drawable.ic_right, android.view.Gravity.END, 0, v -> pager.setCurrentItem(pager.getCurrentItem() + 1, true));
        ovAddLeft = edgeHint(R.drawable.ic_add_circle, android.view.Gravity.START, 44, v -> addPage(0));
        ovAddPage = edgeHint(R.drawable.ic_add_circle, android.view.Gravity.END, 44, v -> addPage(gridPages.size()));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        ((FrameLayout) findViewById(R.id.root)).addView(overviewBar, lp);
    }

    private View edgeHint(int iconRes, int gravity, int offsetDp, View.OnClickListener click) {
        ImageView v = new ImageView(this);
        v.setImageResource(iconRes);
        v.setColorFilter(getResources().getColor(R.color.piko_text_secondary));
        v.setAlpha(0.8f);
        v.setVisibility(View.GONE);
        v.setPadding(dp(4), dp(4), dp(4), dp(4));
        v.setOnClickListener(click);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(36), dp(36), gravity | android.view.Gravity.CENTER_VERTICAL);
        lp.setMargins(0, 0, 0, dp(60 - offsetDp * 2));
        ((FrameLayout) findViewById(R.id.root)).addView(v, lp);
        return v;
    }

    private View ovButton(int iconRes, int label, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(android.view.Gravity.CENTER);
        b.setBackgroundResource(R.drawable.bg_pressable);
        b.setPadding(0, dp(6), 0, dp(6));
        ImageView g = new ImageView(this);
        g.setImageResource(iconRes);
        g.setColorFilter(getResources().getColor(R.color.piko_text_primary));
        g.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
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

    private float ovDownX, ovDownY;
    private boolean ovTap;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (overview) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    android.graphics.Rect r = new android.graphics.Rect();
                    ovTap = pagerRoot.getGlobalVisibleRect(r) && r.contains((int) ev.getRawX(), (int) ev.getRawY());
                    ovDownX = ev.getRawX();
                    ovDownY = ev.getRawY();
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    if (ovTap && Math.hypot(ev.getRawX() - ovDownX, ev.getRawY() - ovDownY) > dp(10)) ovTap = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (ovTap && ev.getEventTime() - ev.getDownTime() < 400) {
                        ovTap = false;
                        MotionEvent c = MotionEvent.obtain(ev);
                        c.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(c);
                        c.recycle();
                        exitOverview();
                        return true;
                    }
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void enterOverview() {
        if (overview) return;
        overview = true;
        for (HomeGridLayout g : gridPages) g.setLongPressEnabled(false);
        searchInput.clearFocus();
        hideKeyboard();
        pagerRoot.setBackgroundResource(R.drawable.bg_page_card);
        pagerRoot.animate().scaleX(0.74f).scaleY(0.74f).translationY(dp(24)).setDuration(240)
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
        for (HomeGridLayout g : gridPages) g.setLongPressEnabled(true);
        pagerRoot.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(220)
                .withEndAction(() -> pagerRoot.setBackground(null)).start();
        overviewBar.animate().alpha(0f).setDuration(180).withEndAction(() -> overviewBar.setVisibility(View.GONE)).start();
        ovAddPage.setVisibility(View.GONE);
        ovAddLeft.setVisibility(View.GONE);
        ovPrev.setVisibility(View.GONE);
        ovNext.setVisibility(View.GONE);
    }

    private void updateOverviewTitle() {
        if (overviewTitle == null) return;
        int idx = pager.getCurrentItem();
        int n = gridPages.size();
        boolean isDefault = n == 1 || idx == defaultIndex();
        String name = n == 1 ? "Home" : "Page " + (idx + 1) + " of " + n;
        overviewTitle.setText((isDefault && n > 1 ? name + " · default" : name) + "\n" + getString(R.string.ov_swipe_hint));
        if (!overview) return;
        boolean hasItems = false;
        for (HomeItem it : homeItems) if (it.page == idx) { hasItems = true; break; }
        ovTidy.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        ovDefault.setVisibility(isDefault ? View.GONE : View.VISIBLE);
        ovDelete.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        ovWidget.setVisibility(View.VISIBLE);
        boolean room = n < 8;
        ovAddPage.setVisibility(room && idx == n - 1 ? View.VISIBLE : View.GONE);
        ovAddLeft.setVisibility(room && idx == 0 ? View.VISIBLE : View.GONE);
        ovPrev.setVisibility(idx > 0 ? View.VISIBLE : View.GONE);
        ovNext.setVisibility(idx < n - 1 ? View.VISIBLE : View.GONE);
    }

    private int defaultIndex() {
        return Math.max(0, Math.min(prefs.defaultPage(), gridPages.size() - 1));
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
        buildPageIndicator(count);
        layoutStore.saveAll(homeItems);
        pager.setCurrentItem(Math.max(0, Math.min(focusIndex, count - 1)), false);
        updatePageIndicator(pager.getCurrentItem());
        updateOverviewTitle();
    }

    /** Inserts an empty grid page at {@code at} (0-based among grid pages) and shows it. */
    private void addPage(int at) {
        if (gridPages.size() >= 8) {
            Toast.makeText(this, "Up to 8 pages", Toast.LENGTH_SHORT).show();
            return;
        }
        for (HomeItem it : homeItems) if (it.page >= at) it.page++;
        if (prefs.defaultPage() >= at) prefs.setDefaultPage(prefs.defaultPage() + 1);
        rebuildGridPages(gridPages.size() + 1, at);
    }

    private void deletePage() {
        final int g = pager.getCurrentItem();
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
                    int def = prefs.defaultPage();
                    prefs.setDefaultPage(def > g ? def - 1 : def == g ? 0 : def);
                    rebuildGridPages(gridPages.size() - 1, Math.min(g, gridPages.size() - 2));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void movePage(int dir) {
        int g = pager.getCurrentItem();
        int t = g + dir;
        if (t < 0 || t >= gridPages.size()) return;
        for (HomeItem it : homeItems) {
            if (it.page == g) it.page = t;
            else if (it.page == t) it.page = g;
        }
        rebuildGridPages(gridPages.size(), t);
    }

    /** Snaps every item on the page back onto the grid, top-to-bottom and left-to-right. */
    private void tidyPage() {
        int g = pager.getCurrentItem();
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
            it.fw = it.fh = -1f;
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
        rebuildGridPages(gridPages.size(), g);
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
        for (HomeGridLayout g : gridPages) g.stopResize();
        if (searchInput != null && !isChangingConfigurations()) searchInput.setText("");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (pager != null) pager.setCurrentItem(defaultIndex(), false);
        if (searchInput != null) {
            searchInput.setText("");
            if (prefs.autoFocusKeyboard()) focusSearch();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && prefs.autoFocusKeyboard() && pager != null && pager.getCurrentItem() == defaultIndex()) {
            focusSearch();
        }
    }

    private void focusSearch() {
        if (searchInput == null || overview) return;
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
        if (pager != null && pager.getCurrentItem() != defaultIndex()) {
            pager.setCurrentItem(defaultIndex(), true);
            return;
        }
        // Home is never "closed" by back — matches standard launcher behaviour.
    }
}
