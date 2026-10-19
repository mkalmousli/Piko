package com.piko.launcher.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Lays children out in rows of a fixed number of columns (GridLayout only exists from Android 4.0). */
public final class ColumnGrid extends LinearLayout {
    private final int columns;
    private int count;

    public ColumnGrid(Context context, int columns) {
        super(context);
        this.columns = columns;
        setOrientation(VERTICAL);
    }

    @Override
    public void addView(View child) {
        if (count % columns == 0) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            super.addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        ((ViewGroup) getChildAt(getChildCount() - 1)).addView(child,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        count++;
    }
}
