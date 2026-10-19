package com.piko.launcher.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.piko.launcher.core.HomeItem;

import java.util.List;

/** Miniature of one home page for the overview strip: a card with a block per item. */
public final class PageThumbView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d = getResources().getDisplayMetrics().density;
    private List<HomeItem> items;
    private int columns = 4, rows = 6, page;
    private boolean selected, home;

    public PageThumbView(Context context) {
        super(context);
    }

    public void bind(List<HomeItem> all, int page, int columns, int rows, boolean selected, boolean home) {
        this.items = all;
        this.page = page;
        this.columns = columns;
        this.rows = rows;
        this.selected = selected;
        this.home = home;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        rect.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(selected ? 0x332A3A66 : 0xFF1B1D22);
        canvas.drawRoundRect(rect, 9 * d, 9 * d, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected ? 2 * d : 1 * d);
        paint.setColor(selected ? 0xFF7C9CFF : 0xFF2A2D34);
        canvas.drawRoundRect(rect, 9 * d, 9 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        if (items != null) {
            float pad = 5 * d, iw = w - 2 * pad, ih = h - 2 * pad;
            for (HomeItem it : items) {
                if (it.page != page) continue;
                float l = it.fx >= 0 ? it.fx : it.col / (float) columns;
                float t = it.fy >= 0 ? it.fy : it.row / (float) rows;
                float cw = it.fw > 0 ? it.fw : it.colSpan / (float) columns;
                float ch = it.fh > 0 ? it.fh : it.rowSpan / (float) rows;
                float inset = 1.2f * d;
                rect.set(pad + l * iw + inset, pad + t * ih + inset, pad + (l + cw) * iw - inset, pad + (t + ch) * ih - inset);
                paint.setColor(it.type == HomeItem.TYPE_WIDGET ? 0x887C9CFF
                        : it.type == HomeItem.TYPE_FOLDER ? 0xFFC98A1E
                        : it.type == HomeItem.TYPE_SHORTCUT ? 0xFF8A5CD6 : 0xFF9AA0AC);
                canvas.drawRoundRect(rect, 3 * d, 3 * d, paint);
            }
        }
        if (home) {
            paint.setColor(0xFF7C9CFF);
            canvas.drawCircle(w - 9 * d, 9 * d, 3.5f * d, paint);
        }
    }
}
