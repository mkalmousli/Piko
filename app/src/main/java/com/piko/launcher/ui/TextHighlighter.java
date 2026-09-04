package com.piko.launcher.ui;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.piko.launcher.R;

/** Bolds and tints every substring of a label that matches a (possibly multi-word) search query. */
public final class TextHighlighter {

    private TextHighlighter() {
    }

    public static CharSequence highlight(android.content.Context context, String label, String rawQuery) {
        if (label == null) label = "";
        if (rawQuery == null || rawQuery.trim().isEmpty()) return label;

        SpannableString spannable = new SpannableString(label);
        String labelLower = label.toLowerCase();
        int color = context.getResources().getColor(R.color.piko_accent);

        boolean any = false;
        for (String word : rawQuery.trim().toLowerCase().split("\\s+")) {
            if (word.isEmpty()) continue;
            int from = 0;
            while (true) {
                int idx = labelLower.indexOf(word, from);
                if (idx < 0) break;
                spannable.setSpan(new ForegroundColorSpan(color), idx, idx + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.BOLD), idx, idx + word.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                any = true;
                from = idx + word.length();
            }
        }
        return any ? spannable : label;
    }
}
