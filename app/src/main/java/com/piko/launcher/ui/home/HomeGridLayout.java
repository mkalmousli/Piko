package com.piko.launcher.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
        /** Fired as soon as the long-press timeout elapses on an occupant. */
        void onItemLongPress(View child);
        /** A visual resize gesture changed an occupant's span; persist it. */
        void onItemResized(View child);
        /** A dragged item dwelt at the left (-1) or right (+1) edge: move it to the neighbouring page. */
        void onItemDragToPage(View child, int direction);
    }

    private final int columns;
    private final int rows;
    private Callbacks callbacks;

    private final int touchSlop;
    /** Movement past this (well under touchSlop) cancels the pending long-press — a swipe always wins. */
    private final int moveCancelThreshold;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Cells stretch to fill the whole page: width/columns by height/rows (not forced square). */
    private int cellW, cellH;
    private View draggingView;
    private float dragOffsetX, dragOffsetY;
    private float dragStartX, dragStartY;
    private boolean dragMoved;

    // Visual resize mode: a bordered frame with a corner handle the user drags to change the span.
    private View resizeView;
    private boolean resizeHandleDown;
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float downX, downY;
    private boolean longPressArmed;
    private boolean longPressFired;
    private Runnable pendingLongPress;

    public HomeGridLayout(Context context, int columns, int rows) {
        super(context);
        this.columns = columns;
        this.rows = rows;
        setChildrenDrawingOrderEnabled(true);
        float density = context.getResources().getDisplayMetrics().density;
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(2 * density);
        framePaint.setColor(0xFF7C9CFF);
        fillPaint.setColor(0x337C9CFF);
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
        return cellW;
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

    /** Enters resize mode for a child: drag the corner handle, tap elsewhere to finish. */
    public void startResize(View child) {
        cancelPendingLongPress();
        resizeView = child;
        resizeHandleDown = false;
        invalidate();
    }

    private RectF resizeRect() {
        GridLayoutParams lp = (GridLayoutParams) resizeView.getLayoutParams();
        return new RectF(lp.col * cellW, lp.row * cellH,
                (lp.col + lp.colSpan) * cellW, (lp.row + lp.rowSpan) * cellH);
    }

    private float handleRadius() {
        return Math.max(Math.min(cellW, cellH) * 0.4f, 24 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (resizeView == null) return;
        RectF r = resizeRect();
        canvas.drawRect(r, fillPaint);
        canvas.drawRect(r, framePaint);
        float density = getResources().getDisplayMetrics().density;
        fillPaint.setColor(0xFF7C9CFF);
        canvas.drawCircle(r.right, r.bottom, 10 * density, fillPaint);
        fillPaint.setColor(0x337C9CFF);
    }

    private boolean handleResizeTouch(MotionEvent ev) {
        GridLayoutParams lp = (GridLayoutParams) resizeView.getLayoutParams();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                RectF r = resizeRect();
                float dx = ev.getX() - r.right, dy = ev.getY() - r.bottom;
                if (dx * dx + dy * dy <= handleRadius() * handleRadius()) {
                    resizeHandleDown = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                } else if (!r.contains(ev.getX(), ev.getY())) {
                    resizeView = null;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!resizeHandleDown) return true;
                int cs = Math.round((ev.getX() - lp.col * cellW) / (float) cellW);
                int rs = Math.round((ev.getY() - lp.row * cellH) / (float) cellH);
                cs = Math.max(1, Math.min(columns - lp.col, cs));
                rs = Math.max(1, Math.min(rows - lp.row, rs));
                if ((cs != lp.colSpan || rs != lp.rowSpan) && isAreaFree(lp.col, lp.row, cs, rs, resizeView)) {
                    lp.colSpan = cs;
                    lp.rowSpan = rs;
                    requestLayout();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (resizeHandleDown && callbacks != null) callbacks.onItemResized(resizeView);
                resizeHandleDown = false;
                return true;
        }
        return true;
    }

    private Runnable edgeDwell;
    private int edgeDirection;

    private void updateEdgeDwell(float x) {
        float zone = 28 * getResources().getDisplayMetrics().density;
        int dir = x < zone ? -1 : (x > getWidth() - zone ? 1 : 0);
        if (dir == edgeDirection && (dir == 0 || edgeDwell != null)) return;
        cancelEdgeDwell();
        if (dir == 0) return;
        edgeDirection = dir;
        edgeDwell = () -> {
            edgeDwell = null;
            View moved = draggingView;
            if (moved == null) return;
            draggingView = null;
            moved.setAlpha(1f);
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (callbacks != null) callbacks.onItemDragToPage(moved, dir);
            requestLayout();
        };
        handler.postDelayed(edgeDwell, 550);
    }

    private void cancelEdgeDwell() {
        if (edgeDwell != null) handler.removeCallbacks(edgeDwell);
        edgeDwell = null;
        edgeDirection = 0;
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
            if (callbacks != null && cellW > 0 && cellH > 0) {
                int col = Math.min(columns - 1, (int) (x / cellW));
                int row = Math.min(rows - 1, (int) (y / cellH));
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
        if (callbacks != null) callbacks.onItemLongPress(child);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (resizeView != null) return true;
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
        if (resizeView != null) return handleResizeTouch(ev);
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
                    int width = lp.colSpan * cellW;
                    int height = lp.rowSpan * cellH;
                    draggingView.layout(newLeft, newTop, newLeft + width, newTop + height);
                    updateEdgeDwell(ev.getX());
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: {
                    cancelEdgeDwell();
                    // An ancestor hijacked the gesture — drop the in-progress drag silently.
                    View dropped = draggingView;
                    draggingView = null;
                    dropped.setAlpha(1f);
                    requestLayout();
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    cancelEdgeDwell();
                    View dropped = draggingView;
                    draggingView = null;
                    dropped.setAlpha(1f);

                    if (!dragMoved) {
                        requestLayout();
                        return true;
                    }

                    GridLayoutParams lp = (GridLayoutParams) dropped.getLayoutParams();

                    boolean draggedOffTop = dropped.getTop() + dropped.getHeight() / 2f < -cellH * 0.4f;
                    if (draggedOffTop && callbacks != null) {
                        callbacks.onItemRemoveRequested(dropped);
                        requestLayout();
                        return true;
                    }

                    int targetCol = Math.max(0, Math.min(columns - 1, Math.round(dropped.getLeft() / (float) cellW)));
                    int targetRow = Math.max(0, Math.min(rows - 1, Math.round(dropped.getTop() / (float) cellH)));
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
        cellW = columns > 0 ? width / columns : width;
        int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? 0 : MeasureSpec.getSize(heightMeasureSpec);
        cellH = available > 0 && rows > 0 ? available / rows : cellW;
        int height = cellH * rows;
        setMeasuredDimension(width, height);

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            GridLayoutParams lp = (GridLayoutParams) child.getLayoutParams();
            child.measure(
                    MeasureSpec.makeMeasureSpec(lp.colSpan * cellW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(lp.rowSpan * cellH, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child == draggingView) continue;
            GridLayoutParams lp = (GridLayoutParams) child.getLayoutParams();
            int left = lp.col * cellW;
            int top = lp.row * cellH;
            child.layout(left, top, left + lp.colSpan * cellW, top + lp.rowSpan * cellH);
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
