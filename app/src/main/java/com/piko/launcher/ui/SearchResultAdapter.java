package com.piko.launcher.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.core.ContactInfo;
import com.piko.launcher.core.IconCache;
import com.piko.launcher.core.SearchResult;
import com.piko.launcher.util.IntentUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the unified search list: app rows (tap to launch) interleaved
 * with contact rows (four inline action buttons — call, SMS, WhatsApp
 * message, WhatsApp call — since there's no reliable way to know in advance
 * which of those a given number actually supports).
 */
public final class SearchResultAdapter extends BaseAdapter {

    public interface Listener {
        void onCall(String phoneNumber);

        /** Fired for any action taken on a contact row, so the caller can teach the search ranker. */
        void onContactActed(ContactInfo contact);
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
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        return items.get(position).type == SearchResult.TYPE_APP;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SearchResult result = items.get(position);
        if (result.type == SearchResult.TYPE_APP) {
            return getAppView(result, convertView, parent);
        }
        return getContactView(result, convertView, parent);
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

    private View getContactView(SearchResult result, View convertView, ViewGroup parent) {
        View view = convertView;
        ContactHolder holder;
        if (view == null || view.getTag() == null || !(view.getTag() instanceof ContactHolder)) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_contact, parent, false);
            holder = new ContactHolder();
            holder.avatar = view.findViewById(R.id.avatar);
            holder.label = view.findViewById(R.id.label);
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
        return view;
    }

    private static final class AppHolder {
        ImageView icon;
        TextView label;
    }

    private static final class ContactHolder {
        TextView avatar;
        TextView label;
        View btnCall, btnSms, btnWaMessage, btnWaCall;
    }
}
