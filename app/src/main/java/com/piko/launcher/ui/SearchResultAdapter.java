package com.piko.launcher.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.ContactInfo;
import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.Prefs;
import com.piko.launcher.core.SearchResult;
import com.piko.launcher.core.WebSearchEngines;
import com.piko.launcher.util.IntentUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the unified search list: app rows (tap to launch), contact rows
 * (four inline action buttons — call, SMS, WhatsApp message, WhatsApp call —
 * plus a long-press menu to copy the name/number), an inline calculator
 * result row, and a "search the web" fallback row.
 */
public final class SearchResultAdapter extends BaseAdapter {

    public interface Listener {
        void onCall(String phoneNumber);

        /** Fired for any action taken on a contact row, so the caller can teach the search ranker. */
        void onContactActed(ContactInfo contact);

        void onWebSearch(String query, String engineId);
    }

    private final Context context;
    private final IconCache iconCache;
    private final int iconSizePx;
    private final Listener listener;
    private List<SearchResult> items = new ArrayList<>();
    private String currentQuery = "";

    public SearchResultAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.iconCache = IconCache.get(context);
        this.iconSizePx = (int) (40 * context.getResources().getDisplayMetrics().density);
    }

    public void submit(List<SearchResult> newItems, String query) {
        this.items = newItems;
        this.selected = -1;
        this.currentQuery = query == null ? "" : query;
        notifyDataSetChanged();
    }

    public SearchResult getResultAt(int position) {
        return items.get(position);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public SearchResult getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 6;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        int type = items.get(position).type;
        return type != SearchResult.TYPE_CONTACT && type != SearchResult.TYPE_CHIPS;
    }

    private int selected = -1;
    private boolean showSelection = true;

    /** The highlight only makes sense while a keyboard (soft or hardware) can drive it. */
    public void setShowSelection(boolean show) {
        if (showSelection == show) return;
        showSelection = show;
        notifyDataSetChanged();
    }

    /** Keyboard selection (display index, -1 = none). */
    public void setSelected(int position) {
        selected = position;
        notifyDataSetChanged();
    }

    public int getSelected() {
        return selected;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = buildView(position, convertView, parent);
        SearchResult r = items.get(position);
        int bg = R.drawable.bg_pressable;
        if (r.type == SearchResult.TYPE_CHIPS) {
            v.setBackgroundResource(R.drawable.bg_group_card);
            return v;
        }
        if (r.group == SearchResult.GROUP_OPEN || r.group == SearchResult.GROUP_FIXED) bg = R.drawable.bg_group_open;
        else if (r.group == SearchResult.GROUP_CHILD) bg = R.drawable.bg_group_child;
        if (showSelection && position == selected) bg = R.drawable.bg_selected;
        v.setBackgroundResource(bg);
        if (r.type == SearchResult.TYPE_ACTION) {
            float d = context.getResources().getDisplayMetrics().density;
            boolean child = r.group == SearchResult.GROUP_CHILD;
            v.setPadding(Math.round((child ? 28 : 8) * d), 0, Math.round(14 * d), 0);
            v.getLayoutParams().height = Math.round((child ? 44 : 52) * d);
        }
        return v;
    }

    private View buildView(int position, View convertView, ViewGroup parent) {
        SearchResult result = items.get(position);
        switch (result.type) {
            case SearchResult.TYPE_APP:
                return getAppView(result, convertView, parent);
            case SearchResult.TYPE_CALC:
                return getCalcView(result, convertView, parent);
            case SearchResult.TYPE_WEB:
                return getWebView(result, convertView, parent);
            case SearchResult.TYPE_ACTION:
                return getActionView(result, convertView, parent);
            case SearchResult.TYPE_CHIPS:
                return getChipsView(result, convertView, parent);
            default:
                return getContactView(result, convertView, parent);
        }
    }

    private View getAppView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        AppHolder holder;
        if (view == null || view.getTag() == null || !(view.getTag() instanceof AppHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_app, parent, false);
            holder = new AppHolder();
            holder.icon = view.findViewById(R.id.icon);
            holder.label = view.findViewById(R.id.label);
            view.setTag(holder);
        } else {
            holder = (AppHolder) view.getTag();
        }
        holder.label.setText(TextHighlighter.highlight(context, result.app.label, currentQuery));
        iconCache.load(result.app, holder.icon, iconSizePx);
        return view;
    }

    private View getActionView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        ActionHolder holder;
        if (view == null || !(view.getTag() instanceof ActionHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_action, parent, false);
            holder = new ActionHolder();
            holder.badge = view.findViewById(R.id.badge);
            holder.badgeIcon = view.findViewById(R.id.badge_icon);
            holder.title = view.findViewById(R.id.title);
            holder.subtitle = view.findViewById(R.id.subtitle);
            holder.chevron = view.findViewById(R.id.chevron);
            view.setTag(holder);
        } else {
            holder = (ActionHolder) view.getTag();
        }
        holder.badge.setText(result.icon == 0 ? result.badge : "");
        holder.badgeIcon.setImageResource(result.icon);
        holder.badgeIcon.setVisibility(result.icon == 0 ? View.GONE : View.VISIBLE);
        holder.title.setText(TextHighlighter.highlight(context, result.text, currentQuery));
        boolean header = result.group == SearchResult.GROUP_HEADER || result.group == SearchResult.GROUP_OPEN;
        boolean open = result.group == SearchResult.GROUP_OPEN || result.group == SearchResult.GROUP_FIXED;
        holder.chevron.setVisibility(header ? View.VISIBLE : View.GONE);
        if (header) {
            holder.chevron.setImageResource(R.drawable.ic_expand);
            com.piko.launcher.util.Compat.setRotation(holder.chevron, open ? 180f : 0f);
            com.piko.launcher.util.Compat.tint(holder.chevron, open ? 0xFF7C9CFF : 0xFF9AA0AC);
        }
        holder.title.setTextColor(open ? 0xFF7C9CFF : 0xFFF5F6F8);
        holder.badge.setTextColor(0xFFFFFFFF);
        holder.subtitle.setText(result.subtitle);
        holder.subtitle.setVisibility(result.subtitle == null || com.piko.launcher.util.Compat.isEmpty(result.subtitle) ? View.GONE : View.VISIBLE);
        return view;
    }

    private View getChipsView(SearchResult result, View convertView, ViewGroup parent) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Math.round(6 * d), Math.round(10 * d), Math.round(6 * d), Math.round(8 * d));
        for (SearchResult.Chip chip : result.chips) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            col.setBackgroundResource(R.drawable.bg_pressable);
            col.setPadding(0, Math.round(4 * d), 0, Math.round(4 * d));

            android.widget.FrameLayout circle = new android.widget.FrameLayout(context);
            android.graphics.drawable.GradientDrawable oval = new android.graphics.drawable.GradientDrawable();
            oval.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            oval.setColor(chip.color);
            com.piko.launcher.util.Compat.setBackground(circle, oval);
            int size = Math.round(44 * d);
            col.addView(circle, new LinearLayout.LayoutParams(size, size));
            if (chip.icon != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(chip.icon);
                com.piko.launcher.util.Compat.tint(icon, 0xFFFFFFFF);
                int is = Math.round(22 * d);
                circle.addView(icon, new android.widget.FrameLayout.LayoutParams(is, is, android.view.Gravity.CENTER));
            } else {
                TextView badge = new TextView(context);
                badge.setText(chip.badge);
                badge.setTextColor(0xFFFFFFFF);
                badge.setTextSize(17);
                badge.setGravity(android.view.Gravity.CENTER);
                circle.addView(badge, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
            TextView label = new TextView(context);
            label.setText(chip.label);
            label.setTextColor(0xFFF5F6F8);
            label.setTextSize(11.5f);
            label.setSingleLine(true);
            label.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.topMargin = Math.round(5 * d);
            col.addView(label, llp);

            col.setOnClickListener(v -> chip.run.run());
            if (chip.onLongPress != null) col.setOnLongClickListener(v -> {
                chip.onLongPress.accept(v);
                return true;
            });
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    private View getCalcView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        CalcHolder holder;
        if (view == null || view.getTag() == null || !(view.getTag() instanceof CalcHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_calc, parent, false);
            holder = new CalcHolder();
            holder.expression = view.findViewById(R.id.expression);
            holder.result = view.findViewById(R.id.result);
            view.setTag(holder);
        } else {
            holder = (CalcHolder) view.getTag();
        }
        holder.expression.setText(result.text);
        holder.result.setText(result.value == null ? "…" : result.value);
        return view;
    }

    private View getWebView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        WebHolder holder;
        if (view == null || view.getTag() == null || !(view.getTag() instanceof WebHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_web, parent, false);
            holder = new WebHolder();
            holder.label = view.findViewById(R.id.label);
            holder.chips = view.findViewById(R.id.engine_chips);
            view.setTag(holder);
        } else {
            holder = (WebHolder) view.getTag();
        }
        holder.label.setText(context.getString(R.string.search_the_web, result.text));

        holder.chips.removeAllViews();
        List<String> engines = Prefs.get(context).enabledSearchEngines();
        int chipSize = (int) (28 * context.getResources().getDisplayMetrics().density);
        for (String engineId : engines) {
            TextView chip = new TextView(context);
            WebSearchEngines.Engine engine = Prefs.get(context).engineById(engineId);
            chip.setText(engine.label.substring(0, 1));
            chip.setTextColor(0xFFFFFFFF);
            chip.setBackgroundResource(R.drawable.bg_shortcut_chip);
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipSize, chipSize);
            lp.setMargins(dp(4), 0, 0, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> listener.onWebSearch(result.text, engineId));
            holder.chips.addView(chip);
        }
        return view;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private View getContactView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        ContactHolder holder;
        if (view == null || view.getTag() == null || !(view.getTag() instanceof ContactHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_contact, parent, false);
            holder = new ContactHolder();
            holder.avatar = view.findViewById(R.id.avatar);
            holder.label = view.findViewById(R.id.label);
            holder.number = view.findViewById(R.id.number);
            holder.btnCall = view.findViewById(R.id.btn_call);
            holder.btnSms = view.findViewById(R.id.btn_sms);
            holder.btnWaMessage = view.findViewById(R.id.btn_wa_message);
            holder.btnWaCall = view.findViewById(R.id.btn_wa_call);
            view.setTag(holder);
        } else {
            holder = (ContactHolder) view.getTag();
        }

        ContactInfo contact = result.contact;
        holder.label.setText(TextHighlighter.highlight(context, contact.name, currentQuery));
        holder.number.setText(contact.phoneNumber);
        holder.number.setVisibility(com.piko.launcher.util.Compat.isEmpty(contact.phoneNumber) ? View.GONE : View.VISIBLE);
        holder.avatar.setText(com.piko.launcher.util.Compat.isEmpty(contact.name) ? "?" : contact.name.substring(0, 1).toUpperCase());
        Prefs prefs = Prefs.get(context);
        holder.btnCall.setVisibility(prefs.flag(Prefs.FLAG_ACTION_CALL) ? View.VISIBLE : View.GONE);
        holder.btnSms.setVisibility(prefs.flag(Prefs.FLAG_ACTION_SMS) ? View.VISIBLE : View.GONE);
        holder.btnWaMessage.setVisibility(prefs.flag(Prefs.FLAG_ACTION_WA_MESSAGE) ? View.VISIBLE : View.GONE);
        holder.btnWaCall.setVisibility(prefs.flag(Prefs.FLAG_ACTION_WA_CALL) ? View.VISIBLE : View.GONE);
        holder.btnCall.setOnClickListener(v -> {
            listener.onCall(contact.phoneNumber);
            listener.onContactActed(contact);
        });
        holder.btnSms.setOnClickListener(v -> {
            IntentUtil.sendSms(context, contact.phoneNumber);
            listener.onContactActed(contact);
        });
        holder.btnWaMessage.setOnClickListener(v -> {
            IntentUtil.whatsAppMessage(context, contact.phoneNumber);
            listener.onContactActed(contact);
        });
        holder.btnWaCall.setOnClickListener(v -> {
            IntentUtil.whatsAppCall(context, contact.phoneNumber, false);
            listener.onContactActed(contact);
        });
        // Tapping the row (outside the action buttons/avatar) opens the contact's own detail page.
        view.setOnClickListener(v -> {
            switch (prefs.contactTapAction()) {
                case Prefs.TAP_NONE:
                    return;
                case Prefs.TAP_CALL:
                    listener.onCall(contact.phoneNumber);
                    break;
                case Prefs.TAP_COPY:
                    IntentUtil.copyToClipboard(context, "number", contact.phoneNumber);
                    break;
                default:
                    IntentUtil.openContactInfo(context, contact.contactId);
            }
            listener.onContactActed(contact);
        });
        // A long press is a quick "grab the number" gesture — copy it straight away, no menu.
        view.setOnLongClickListener(v -> {
            IntentUtil.copyToClipboard(context, "number", contact.phoneNumber);
            return true;
        });
        // The avatar is the one thing worth blowing up full-screen for reading a number aloud.
        holder.avatar.setOnClickListener(v -> BigNumberActivity.start(context, contact.name, contact.phoneNumber));
        return view;
    }

    private static final class AppHolder {
        ImageView icon;
        TextView label;
    }

    private static final class ActionHolder {
        TextView badge, title, subtitle;
        android.widget.ImageView badgeIcon, chevron;
    }

    private static final class CalcHolder {
        TextView expression;
        TextView result;
    }

    private static final class WebHolder {
        TextView label;
        LinearLayout chips;
    }

    private static final class ContactHolder {
        TextView avatar;
        TextView label;
        TextView number;
        View btnCall, btnSms, btnWaMessage, btnWaCall;
    }
}
