package com.piko.launcher.ui.home;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;

import com.piko.launcher.core.HomeItem;

/**
 * A fixed-size icon/widget grid for one home page. Positioning is cell-based
 * (see {@link GridLayoutParams}); long-pressing an empty cell asks the host
 * to offer "add app / add widget", long-pressing an occupant starts a manual
 * drag-to-reorder gesture (no AndroidX drag helpers exist to lean on).
 *
 * Long-press detection is hand-rolled (not {@link android.view.GestureDetector})
 * so it can be reliably suppressed the moment the touch drifts horizontally —
 * the page swiper (HorizontalPager) only ever gets to steal a gesture once it
 * exceeds its own touch slop, which can take longer than the long-press
 * timeout on a slow, deliberate swipe. Self-cancelling on a much smaller
 * drift than that slop means a swipe reliably wins the race instead of
 * occasionally popping a context menu mid-swipe.
 */
public final class HomeGridLayout extends ViewGroup {

    public interface Callbacks {
        void onEmptyCellLongPress(int col, int row);
        void onItemMoved(View child, int newCol, int newRow);
        void onItemRemoveRequested(View child);
        /** Fired when a long-press on an occupant ends without the finger having actually dragged it. */
        void onItemLongPress(View child);
    }

    private final int columns;
    private final int rows;
    private Callbacks callbacks;

    private final int touchSlop;
    /** Movement past this (well under touchSlop) cancels the pending long-press — a swipe always wins. */
    private final int moveCancelThreshold;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int cellSize;
    private View draggingView;
    private float dragOffsetX, dragOffsetY;
    private float dragStartX, dragStartY;
    private boolean dragMoved;

    private float downX, downY;
    private boolean longPressArmed;
    private boolean longPressFired;
    private Runnable pendingLongPress;

    public HomeGridLayout(Context context, int columns, int rows) {
        super(context);
        this.columns = columns;
        this.rows = rows;
        setChildrenDrawingOrderEnabled(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        moveCancelThreshold = Math.max(4, touchSlop / 2);
    }

    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getCellSize() {
        return cellSize;
    }

    public GridLayoutParams gridParams(int col, int row, int colSpan, int rowSpan) {
        return new GridLayoutParams(col, row, colSpan, rowSpan);
    }

    /** Applies a new cell placement/span to an existing occupant (e.g. after a resize) and re-lays it out. */
    public void updateViewParams(View child, GridLayoutParams params) {
        child.setLayoutParams(params);
        requestLayout();
    }

    public boolean isAreaFree(int col, int row, int colSpan, int rowSpan, View ignore) {
        if (col < 0 || row < 0 || col + colSpan > columns || row + rowSpan > rows) return false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == ignore) continue;
            GridLayoutParams lp = (GridLayoutParams) child.getLayoutParams();
            boolean overlapsCols = col < lp.col + lp.colSpan && col + colSpan > lp.col;
            boolean overlapsRows = row < lp.row + lp.rowSpan && row + rowSpan > lp.row;
            if (overlapsCols && overlapsRows) return false;
        }
        return true;
    }

    private View childAt(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            Rect r = new Rect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            if (r.contains((int) x, (int) y)) return child;
        }
        return null;
    }

    private void scheduleLongPress(float x, float y) {
        downX = x;
        downY = y;
        longPressArmed = true;
        longPressFired = false;
        pendingLongPress = () -> {
            if (!longPressArmed) return;
            longPressFired = true;
            fireLongPress(downX, downY);
        };
        handler.postDelayed(pendingLongPress, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelPendingLongPress() {
        longPressArmed = false;
        if (pendingLongPress != null) {
            handler.removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    private void fireLongPress(float x, float y) {
        View child = childAt(x, y);
        if (child == null) {
            if (callbacks != null && cellSize > 0) {
                int col = Math.min(columns - 1, (int) (x / cellSize));
                int row = Math.min(rows - 1, (int) (y / cellSize));
                callbacks.onEmptyCellLongPress(col, row);
            }
            return;
        }
        draggingView = child;
        dragMoved = false;
        dragStartX = x;
        dragStartY = y;
        dragOffsetX = x - child.getLeft();
        dragOffsetY = y - child.getTop();
        child.setAlpha(0.75f);
        child.bringToFront();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scheduleLongPress(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                if (longPressArmed && !longPressFired) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (dx * dx + dy * dy > moveCancelThreshold * moveCancelThreshold) {
                        cancelPendingLongPress();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!longPressFired) cancelPendingLongPress();
                break;
        }
        return draggingView != null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (draggingView != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    if (!dragMoved) {
                        float dx = ev.getX() - dragStartX;
                        float dy = ev.getY() - dragStartY;
                        if (dx * dx + dy * dy > touchSlop * touchSlop) dragMoved = true;
                    }
                    int newLeft = (int) (ev.getX() - dragOffsetX);
                    int newTop = (int) (ev.getY() - dragOffsetY);
                    GridLayoutParams lp = (GridLayoutParams) draggingView.getLayoutParams();
                    int width = lp.colSpan * cellSize;
                    int height = lp.rowSpan * cellSize;
                    draggingView.layout(newLeft, newTop, newLeft + width, newTop + height);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: {
                    // An ancestor hijacked the gesture — drop the in-progress drag silently.
                    View dropped = draggingView;
                    draggingView = null;
                    dropped.setAlpha(1f);
                    requestLayout();
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    View dropped = draggingView;
                    draggingView = null;
                    dropped.setAlpha(1f);

                    if (!dragMoved) {
                        requestLayout();
                        if (callbacks != null) callbacks.onItemLongPress(dropped);
                        return true;
                    }

                    GridLayoutParams lp = (GridLayoutParams) dropped.getLayoutParams();

                    boolean draggedOffTop = dropped.getTop() + dropped.getHeight() / 2f < -cellSize * 0.4f;
                    if (draggedOffTop && callbacks != null) {
                        callbacks.onItemRemoveRequested(dropped);
                        requestLayout();
                        return true;
                    }

                    int targetCol = Math.max(0, Math.min(columns - 1, Math.round(dropped.getLeft() / (float) cellSize)));
                    int targetRow = Math.max(0, Math.min(rows - 1, Math.round(dropped.getTop() / (float) cellSize)));
                    if (isAreaFree(targetCol, targetRow, lp.colSpan, lp.rowSpan, dropped)) {
                        lp.col = targetCol;
                        lp.row = targetRow;
                        if (callbacks != null) callbacks.onItemMoved(dropped, targetCol, targetRow);
                    }
                    requestLayout();
                    return true;
                }
            }
            return true;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        cellSize = columns > 0 ? width / columns : width;
        int height = cellSize * rows;
        setMeasuredDimension(width, height);

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            GridLayoutParams lp = (GridLayoutParams) child.getLayoutParams();
            child.measure(
                    MeasureSpec.makeMeasureSpec(lp.colSpan * cellSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(lp.rowSpan * cellSize, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == draggingView) continue;
            GridLayoutParams lp = (GridLayoutParams) child.getLayoutParams();
            int left = lp.col * cellSize;
            int top = lp.row * cellSize;
            child.layout(left, top, left + lp.colSpan * cellSize, top + lp.rowSpan * cellSize);
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new GridLayoutParams(0, 0, 1, 1);
    }

    /** Cell-based layout params: which grid cell an occupant starts at, and how many it spans. */
    public static final class GridLayoutParams extends ViewGroup.LayoutParams {
        public int col, row, colSpan, rowSpan;

        public GridLayoutParams(int col, int row, int colSpan, int rowSpan) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            this.col = col;
            this.row = row;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }
    }
}
