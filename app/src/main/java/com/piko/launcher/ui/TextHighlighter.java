package com.piko.launcher.ui;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.piko.launcher.R;

/** Bolds and tints every substring of a label that matches a (possibly multi-word) search query. */
public final class TextHighlighter {

    private TextHighlighter() {
    }

    public static CharSequence highlight(android.content.Context context, String label, String rawQuery) {
        if (label == null) label = "";
        if (rawQuery == null || com.piko.launcher.util.Compat.isEmpty(rawQuery.trim())) return label;

        SpannableString spannable = new SpannableString(label);
        String labelLower = label.toLowerCase();
        int color = context.getResources().getColor(R.color.piko_accent);

        boolean any = false;
        for (String word : rawQuery.trim().toLowerCase().split("\\s+")) {
            if (com.piko.launcher.util.Compat.isEmpty(word)) continue;
            int from = 0;
            while (true) {
                int idx = labelLower.indexOf(word, from);
                if (idx < 0) break;
                spannable.setSpan(new BackgroundColorSpan((color & 0xFFFFFF) | 0x33000000), idx, idx + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(color), idx, idx + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                any = true;
                from = idx + word.length();
            }
        }
        if (!any) any = highlightDigits(spannable, labelLower, rawQuery, color);
        return any ? spannable : label;
    }

    /** Phone numbers: match the typed digits across spaces, dashes and brackets in the stored number. */
    private static boolean highlightDigits(Spannable spannable, String label, String rawQuery, int color) {
        String qd = rawQuery.replaceAll("[^0-9]", "");
        if (qd.length() < 2 || !rawQuery.matches("[0-9+\\-\\s().]+")) return false;
        StringBuilder digits = new StringBuilder();
        java.util.ArrayList<Integer> pos = new java.util.ArrayList<>();
        for (int i = 0; i < label.length(); i++) {
            if (Character.isDigit(label.charAt(i))) {
                digits.append(label.charAt(i));
                pos.add(i);
            }
        }
        int idx = digits.indexOf(qd);
        if (idx < 0) return false;
        int start = pos.get(idx), end = pos.get(idx + qd.length() - 1) + 1;
        spannable.setSpan(new BackgroundColorSpan((color & 0xFFFFFF) | 0x33000000), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return true;
    }
}
