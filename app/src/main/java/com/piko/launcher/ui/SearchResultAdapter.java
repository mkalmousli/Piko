package com.piko.launcher.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.ContactInfo;
import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.SearchResult;
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

        void onWebSearch(String query);
    }

    private final Context context;
    private final IconCache iconCache;
    private final int iconSizePx;
    private final Listener listener;
    private List<SearchResult> items = new ArrayList<>();

    public SearchResultAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.iconCache = IconCache.get(context);
        this.iconSizePx = (int) (40 * context.getResources().getDisplayMetrics().density);
    }

    public void submit(List<SearchResult> newItems) {
        this.items = newItems;
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
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        int type = items.get(position).type;
        return type == SearchResult.TYPE_APP || type == SearchResult.TYPE_CALC || type == SearchResult.TYPE_WEB;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SearchResult result = items.get(position);
        switch (result.type) {
            case SearchResult.TYPE_APP:
                return getAppView(result, convertView, parent);
            case SearchResult.TYPE_CALC:
                return getCalcView(result, convertView, parent);
            case SearchResult.TYPE_WEB:
                return getWebView(result, convertView, parent);
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
        holder.label.setText(result.app.label);
        iconCache.load(result.app, holder.icon, iconSizePx);
        return view;
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
            view.setTag(holder);
        } else {
            holder = (WebHolder) view.getTag();
        }
        holder.label.setText(context.getString(R.string.search_the_web, result.text));
        return view;
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
        holder.label.setText(contact.name);
        holder.number.setText(contact.phoneNumber);
        holder.number.setVisibility(contact.phoneNumber.isEmpty() ? View.GONE : View.VISIBLE);
        holder.avatar.setText(contact.name.isEmpty() ? "?" : contact.name.substring(0, 1).toUpperCase());
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
        view.setOnLongClickListener(v -> {
            showContactContextMenu(v, contact);
            return true;
        });
        return view;
    }

    private void showContactContextMenu(View anchor, ContactInfo contact) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, R.string.copy_name);
        menu.getMenu().add(0, 2, 1, R.string.copy_number);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                IntentUtil.copyToClipboard(context, "name", contact.name);
                return true;
            } else if (item.getItemId() == 2) {
                IntentUtil.copyToClipboard(context, "number", contact.phoneNumber);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private static final class AppHolder {
        ImageView icon;
        TextView label;
    }

    private static final class CalcHolder {
        TextView expression;
        TextView result;
    }

    private static final class WebHolder {
        TextView label;
    }

    private static final class ContactHolder {
        TextView avatar;
        TextView label;
        TextView number;
        View btnCall, btnSms, btnWaMessage, btnWaCall;
    }
}
