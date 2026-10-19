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
        /** Action ids (ACTION_*) offered in the bar that appears at the top while an item is dragged. */
        int[] actionsFor(View child);
        /** The item was dropped on one of the action tiles. */
        void onItemAction(View child, int action);
        /** An occupant was long-pressed and released without being dragged: show its menu. */
        void onItemLongPress(View child);
        /** A visual resize gesture changed an occupant's span; persist it. */
        void onItemResized(View child);
        /**
         * A dragged item dwelt at the left (-1) or right (+1) edge: move it to the neighbouring page.
         * Returns the grid that now owns the view (which continues the drag), or null if it can't move.
         */
        HomeGridLayout onItemDragToPage(View child, int direction);
    }

    public static final int ACTION_REMOVE = 1, ACTION_UNINSTALL = 2, ACTION_INFO = 3,
            ACTION_EDIT = 4, ACTION_RESIZE = 5, ACTION_NAME = 6;

    private final int columns;
    private boolean freeform;
    private int[] actions;
    private int activeAction;
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
    private float lastRawX, lastRawY;
    private boolean dragMoved;
    /** Grid that took over the drag after an edge page-turn; this grid keeps receiving the touch stream. */
    private HomeGridLayout proxy;
    private Drop dropTarget;
    private boolean removeArmed, inRemoveZone;
    private boolean edgeArmed;
    /** After a page-turn the pager is still sliding, so edge readings are meaningless until it settles. */
    private long edgeBlockedUntil;
    private final RectF tmpRect = new RectF();
    private final int[] loc = new int[2];
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String removeLabel;

    private static final class Drop {
        int col, row;
        View swap;
        boolean valid;
    }

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
        // Same threshold the pager uses to steal a swipe, so exactly one of the two gestures wins.
        moveCancelThreshold = touchSlop;
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(11 * density);
        removeLabel = context.getString(com.piko.launcher.R.string.drop_to_remove);
    }

    public void setFreeform(boolean freeform) {
        this.freeform = freeform;
    }

    private int leftOf(GridLayoutParams lp) {
        if (freeform && lp.fx >= 0) {
            int w = lp.colSpan * cellW;
            return Math.max(0, Math.min(getMeasuredWidth() - w, Math.round(lp.fx * getMeasuredWidth())));
        }
        return lp.col * cellW;
    }

    private int topOf(GridLayoutParams lp) {
        if (freeform && lp.fy >= 0) {
            int h = lp.rowSpan * cellH;
            return Math.max(0, Math.min(getMeasuredHeight() - h, Math.round(lp.fy * getMeasuredHeight())));
        }
        return lp.row * cellH;
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
        return isAreaFree(col, row, colSpan, rowSpan, ignore, null);
    }

    private boolean isAreaFree(int col, int row, int colSpan, int rowSpan, View ignore, View ignore2) {
        if (col < 0 || row < 0 || col + colSpan > columns || row + rowSpan > rows) return false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == ignore || child == ignore2) continue;
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
        int l = leftOf(lp), t = topOf(lp);
        return new RectF(l, t, l + lp.colSpan * cellW, t + lp.rowSpan * cellH);
    }

    private float handleRadius() {
        return Math.max(Math.min(cellW, cellH) * 0.4f, 24 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        if (draggingView != null && !freeform && dropTarget != null && dropTarget.valid) {
            tmpRect.set(dropTarget.col * cellW, dropTarget.row * cellH,
                    (dropTarget.col + spanOf(draggingView, true)) * cellW,
                    (dropTarget.row + spanOf(draggingView, false)) * cellH);
            tmpRect.inset(4 * density, 4 * density);
            int color = dropTarget.swap != null ? 0xFFFFB74D : 0xFF7C9CFF;
            fillPaint.setColor((color & 0xFFFFFF) | 0x33000000);
            canvas.drawRoundRect(tmpRect, 14 * density, 14 * density, fillPaint);
            framePaint.setColor(color);
            canvas.drawRoundRect(tmpRect, 14 * density, 14 * density, framePaint);
            framePaint.setColor(0xFF7C9CFF);
            fillPaint.setColor(0x337C9CFF);
        }
        super.dispatchDraw(canvas);
        if (draggingView != null) {
            if (actions != null && actions.length > 0) drawActionBar(canvas, density);
            if (edgeDirection != 0) {
                float w = 20 * density;
                fillPaint.setColor(0x557C9CFF);
                if (edgeDirection < 0) canvas.drawRect(0, 0, w, getHeight(), fillPaint);
                else canvas.drawRect(getWidth() - w, 0, getWidth(), getHeight(), fillPaint);
                fillPaint.setColor(0x337C9CFF);
            }
        }
        if (resizeView == null) return;
        RectF r = resizeRect();
        canvas.drawRect(r, fillPaint);
        canvas.drawRect(r, framePaint);
        fillPaint.setColor(0xFF7C9CFF);
        canvas.drawCircle(r.right, r.bottom, 10 * density, fillPaint);
        fillPaint.setColor(0x337C9CFF);
    }

    private static int actionIcon(int a) {
        switch (a) {
            case ACTION_REMOVE: return com.piko.launcher.R.drawable.ic_close;
            case ACTION_UNINSTALL: return com.piko.launcher.R.drawable.ic_trash;
            case ACTION_INFO: return com.piko.launcher.R.drawable.ic_info;
            case ACTION_EDIT: return com.piko.launcher.R.drawable.ic_edit;
            case ACTION_RESIZE: return com.piko.launcher.R.drawable.ic_resize;
            default: return com.piko.launcher.R.drawable.ic_text;
        }
    }

    private String actionLabel(int a) {
        int res;
        switch (a) {
            case ACTION_REMOVE: res = com.piko.launcher.R.string.act_remove; break;
            case ACTION_UNINSTALL: res = com.piko.launcher.R.string.act_uninstall; break;
            case ACTION_INFO: res = com.piko.launcher.R.string.act_info; break;
            case ACTION_EDIT: res = com.piko.launcher.R.string.act_edit; break;
            case ACTION_RESIZE: res = com.piko.launcher.R.string.act_resize; break;
            default: res = com.piko.launcher.R.string.act_name;
        }
        return getResources().getString(res);
    }

    /** Tiles across the top: faint until the finger has left the strip once, then lit under the finger. */
    private void drawActionBar(Canvas canvas, float density) {
        float h = removeZoneHeight();
        int n = actions.length;
        float tileW = getWidth() / (float) n;
        fillPaint.setColor(removeArmed ? 0xEE15181F : 0x8815181F);
        canvas.drawRect(0, 0, getWidth(), h, fillPaint);
        for (int i = 0; i < n; i++) {
            boolean active = removeArmed && actions[i] == activeAction;
            if (active) {
                fillPaint.setColor(actions[i] == ACTION_REMOVE || actions[i] == ACTION_UNINSTALL ? 0xFFE53935 : 0xFF7C9CFF);
                tmpRect.set(i * tileW + 4 * density, 4 * density, (i + 1) * tileW - 4 * density, h - 4 * density);
                canvas.drawRoundRect(tmpRect, 12 * density, 12 * density, fillPaint);
            }
            textPaint.setAlpha(removeArmed ? 255 : 120);
            android.graphics.drawable.Drawable ic = getContext().getDrawable(actionIcon(actions[i])).mutate();
            int cx = (int) (i * tileW + tileW / 2f), cy = (int) (h / 2f - 4 * density), r = (int) (10 * density);
            ic.setBounds(cx - r, cy - r, cx + r, cy + r);
            ic.setAlpha(removeArmed ? 255 : 120);
            ic.draw(canvas);
            textPaint.setTextSize(10.5f * density);
            canvas.drawText(actionLabel(actions[i]), i * tileW + tileW / 2f, h - 10 * density, textPaint);
        }
        textPaint.setAlpha(255);
        textPaint.setTextSize(11 * density);
        fillPaint.setColor(0x337C9CFF);
    }

    private static int spanOf(View v, boolean cols) {
        GridLayoutParams lp = (GridLayoutParams) v.getLayoutParams();
        return cols ? lp.colSpan : lp.rowSpan;
    }

    private float removeZoneHeight() {
        return 64 * getResources().getDisplayMetrics().density;
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
                int l = leftOf(lp), t = topOf(lp);
                int cs = Math.round((ev.getX() - l) / (float) cellW);
                int rs = Math.round((ev.getY() - t) / (float) cellH);
                cs = Math.max(1, Math.min((getMeasuredWidth() - l) / cellW, cs));
                rs = Math.max(1, Math.min((getMeasuredHeight() - t) / cellH, rs));
                if ((cs != lp.colSpan || rs != lp.rowSpan)
                        && (freeform || isAreaFree(lp.col, lp.row, cs, rs, resizeView))) {
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
        float zone = 36 * getResources().getDisplayMetrics().density;
        int raw = x < zone ? -1 : (x > getWidth() - zone ? 1 : 0);
        if (raw == 0 && android.os.SystemClock.uptimeMillis() > edgeBlockedUntil) edgeArmed = true;
        final int dir = edgeArmed ? raw : 0;
        if (dir == edgeDirection && (dir == 0 || edgeDwell != null)) return;
        cancelEdgeDwell();
        invalidate();
        if (dir == 0) return;
        edgeDirection = dir;
        edgeDwell = () -> {
            edgeDwell = null;
            View moved = draggingView;
            if (moved == null || callbacks == null) return;
            HomeGridLayout next = callbacks.onItemDragToPage(moved, dir);
            edgeDirection = 0;
            if (next == null) {
                edgeArmed = false;
                return;
            }
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            draggingView = null;
            dropTarget = null;
            inRemoveZone = false;
            proxy = next;
            next.adoptDrag(moved, dragOffsetX, dragOffsetY, lastRawX, lastRawY);
            invalidate();
        };
        handler.postDelayed(edgeDwell, 420);
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

    /** Converts screen coordinates to this grid's local space, independent of any page transform. */
    private void toLocal(float rawX, float rawY, float[] out) {
        View parent = (View) getParent();
        parent.getLocationOnScreen(loc);
        float scale = 1f;
        for (View v = this; v.getParent() instanceof View; v = (View) v.getParent()) scale *= v.getScaleX();
        out[0] = (rawX - loc[0]) / scale - (getLeft() - parent.getScrollX());
        out[1] = (rawY - loc[1]) / scale - (getTop() - parent.getScrollY());
    }

    private final float[] tmpLocal = new float[2];

    private void fireLongPress(float x, float y) {
        View child = childAt(x, y);
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
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
        removeArmed = false;
        inRemoveZone = false;
        activeAction = 0;
        actions = callbacks != null ? callbacks.actionsFor(child) : null;
        edgeArmed = false;
        lift(child);
        child.bringToFront();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        dropTarget = resolveDrop(child);
        startTick();
        invalidate();
    }

    /** Picks the dragged view up: scaled, slightly see-through and tilted toward the viewer. */
    private void lift(View v) {
        v.animate().cancel();
        v.setCameraDistance(9000 * getResources().getDisplayMetrics().density);
        v.animate().scaleX(1.15f).scaleY(1.15f).alpha(0.92f).rotationX(-6f).setDuration(120).start();
    }

    private void drop(View v) {
        v.animate().cancel();
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).rotationX(0f).translationX(0f).translationY(0f)
                .setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Called on the target grid once an edge page-turn hands the in-flight drag over. */
    void adoptDrag(View child, float grabX, float grabY, float rawX, float rawY) {
        draggingView = child;
        dragMoved = true;
        dragOffsetX = grabX;
        dragOffsetY = grabY;
        lastRawX = rawX;
        lastRawY = rawY;
        removeArmed = false;
        inRemoveZone = false;
        activeAction = 0;
        actions = callbacks != null ? callbacks.actionsFor(child) : null;
        edgeArmed = false;
        edgeBlockedUntil = android.os.SystemClock.uptimeMillis() + 700;
        lift(child);
        child.bringToFront();
        toLocal(rawX, rawY, tmpLocal);
        positionDragged(tmpLocal[0], tmpLocal[1]);
        startTick();
    }

    private Runnable tick;

    /** Re-applies the finger position every frame so a dragged item tracks it while the pager scrolls underneath. */
    private void startTick() {
        if (tick != null) return;
        tick = new Runnable() {
            @Override
            public void run() {
                if (draggingView == null) {
                    tick = null;
                    return;
                }
                toLocal(lastRawX, lastRawY, tmpLocal);
                positionDragged(tmpLocal[0], tmpLocal[1]);
                postOnAnimation(this);
            }
        };
        postOnAnimation(tick);
    }

    private void positionDragged(float x, float y) {
        View v = draggingView;
        if (v == null) return;
        GridLayoutParams lp = (GridLayoutParams) v.getLayoutParams();
        int w = lp.colSpan * cellW, h = lp.rowSpan * cellH;
        int left = (int) (x - dragOffsetX), top = (int) (y - dragOffsetY);
        if (v.getLeft() != left || v.getTop() != top) {
            v.layout(left, top, left + w, top + h);
        }
        if (!dragMoved) {
            float dx = x - dragStartX, dy = y - dragStartY;
            if (dx * dx + dy * dy > touchSlop * touchSlop) dragMoved = true;
        }
        float zone = removeZoneHeight();
        if (y > zone) removeArmed = true;
        boolean remove = removeArmed && y < zone && actions != null && actions.length > 0;
        int act = remove ? actions[Math.max(0, Math.min(actions.length - 1, (int) (x / (getWidth() / (float) actions.length))))] : 0;
        Drop d = remove || freeform ? null : resolveDrop(v);
        boolean changed = remove != inRemoveZone || act != activeAction || !sameDrop(d, dropTarget);
        if (act != activeAction) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        activeAction = act;
        inRemoveZone = remove;
        dropTarget = d;
        if (changed) {
            if (d != null && d.valid) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
        }
        updateEdgeDwell(x);
    }

    private static boolean sameDrop(Drop a, Drop b) {
        if (a == null || b == null) return a == b;
        return a.col == b.col && a.row == b.row && a.swap == b.swap && a.valid == b.valid;
    }

    /** Where the dragged view would land if released now: its cell, a swap with the occupant, or the nearest free spot. */
    private Drop resolveDrop(View dragged) {
        GridLayoutParams lp = (GridLayoutParams) dragged.getLayoutParams();
        Drop d = new Drop();
        if (cellW == 0 || cellH == 0) return d;
        int col = Math.max(0, Math.min(columns - lp.colSpan, Math.round(dragged.getLeft() / (float) cellW)));
        int row = Math.max(0, Math.min(rows - lp.rowSpan, Math.round(dragged.getTop() / (float) cellH)));
        d.col = col;
        d.row = row;
        if (isAreaFree(col, row, lp.colSpan, lp.rowSpan, dragged)) {
            d.valid = true;
            return d;
        }
        View occ = null;
        int overlaps = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c == dragged) continue;
            GridLayoutParams cl = (GridLayoutParams) c.getLayoutParams();
            if (col < cl.col + cl.colSpan && col + lp.colSpan > cl.col
                    && row < cl.row + cl.rowSpan && row + lp.rowSpan > cl.row) {
                occ = c;
                overlaps++;
            }
        }
        if (overlaps == 1) {
            GridLayoutParams ol = (GridLayoutParams) occ.getLayoutParams();
            boolean apart = lp.col + ol.colSpan <= col || col + lp.colSpan <= lp.col
                    || lp.row + ol.rowSpan <= row || row + lp.rowSpan <= lp.row;
            if (apart && isAreaFree(col, row, lp.colSpan, lp.rowSpan, dragged, occ)
                    && isAreaFree(lp.col, lp.row, ol.colSpan, ol.rowSpan, dragged, occ)) {
                d.swap = occ;
                d.valid = true;
                return d;
            }
        }
        int best = Integer.MAX_VALUE;
        for (int r = 0; r <= rows - lp.rowSpan; r++) {
            for (int c = 0; c <= columns - lp.colSpan; c++) {
                if (!isAreaFree(c, r, lp.colSpan, lp.rowSpan, dragged)) continue;
                int dist = (c - col) * (c - col) + (r - row) * (r - row);
                if (dist < best && dist <= 4) {
                    best = dist;
                    d.col = c;
                    d.row = r;
                    d.valid = true;
                }
            }
        }
        return d;
    }

    /** Moves a view to the cell stored in its params, animating from where it visually was. */
    private void settleInto(View v, int fromLeft, int fromTop) {
        GridLayoutParams lp = (GridLayoutParams) v.getLayoutParams();
        int left = leftOf(lp), top = topOf(lp);
        v.layout(left, top, left + lp.colSpan * cellW, top + lp.rowSpan * cellH);
        v.setTranslationX(fromLeft - left);
        v.setTranslationY(fromTop - top);
        drop(v);
    }

    private void finishDrag(boolean cancelled) {
        cancelEdgeDwell();
        View v = draggingView;
        draggingView = null;
        boolean wasRemove = inRemoveZone;
        int action = activeAction;
        activeAction = 0;
        actions = null;
        Drop d = dropTarget;
        dropTarget = null;
        inRemoveZone = false;
        removeArmed = false;
        invalidate();
        if (v == null) return;
        int fromLeft = v.getLeft(), fromTop = v.getTop();
        GridLayoutParams lp = (GridLayoutParams) v.getLayoutParams();

        if (cancelled) {
            settleInto(v, fromLeft, fromTop);
            return;
        }
        if (!dragMoved) {
            settleInto(v, fromLeft, fromTop);
            if (callbacks != null) callbacks.onItemLongPress(v);
            return;
        }
        if (wasRemove && callbacks != null) {
            if (action == ACTION_REMOVE) {
                drop(v);
                callbacks.onItemAction(v, action);
                requestLayout();
            } else {
                settleInto(v, fromLeft, fromTop);
                callbacks.onItemAction(v, action);
            }
            return;
        }
        if (freeform) {
            int vw = lp.colSpan * cellW, vh = lp.rowSpan * cellH;
            int left = Math.max(0, Math.min(getWidth() - vw, fromLeft));
            int top = Math.max(0, Math.min(getHeight() - vh, fromTop));
            lp.fx = left / (float) getWidth();
            lp.fy = top / (float) getHeight();
            lp.col = Math.max(0, Math.min(columns - lp.colSpan, Math.round(left / (float) cellW)));
            lp.row = Math.max(0, Math.min(rows - lp.rowSpan, Math.round(top / (float) cellH)));
            if (callbacks != null) callbacks.onItemMoved(v, lp.col, lp.row);
            settleInto(v, fromLeft, fromTop);
            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            return;
        }
        if (d != null && d.valid) {
            if (d.swap != null) {
                GridLayoutParams ol = (GridLayoutParams) d.swap.getLayoutParams();
                int oldL = d.swap.getLeft(), oldT = d.swap.getTop();
                ol.col = lp.col;
                ol.row = lp.row;
                lp.col = d.col;
                lp.row = d.row;
                if (callbacks != null) {
                    callbacks.onItemMoved(d.swap, ol.col, ol.row);
                    callbacks.onItemMoved(v, lp.col, lp.row);
                }
                settleInto(d.swap, oldL, oldT);
            } else if (d.col != lp.col || d.row != lp.row) {
                lp.col = d.col;
                lp.row = d.row;
                if (callbacks != null) callbacks.onItemMoved(v, lp.col, lp.row);
            }
        }
        settleInto(v, fromLeft, fromTop);
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
    }

    private void dragEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                toLocal(lastRawX, lastRawY, tmpLocal);
                positionDragged(tmpLocal[0], tmpLocal[1]);
                break;
            case MotionEvent.ACTION_UP:
                finishDrag(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                finishDrag(true);
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (resizeView != null) return true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scheduleLongPress(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                trackLongPressDrift(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!longPressFired) cancelPendingLongPress();
                break;
        }
        return draggingView != null || proxy != null;
    }

    private void trackLongPressDrift(MotionEvent ev) {
        if (longPressArmed && !longPressFired) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            if (dx * dx + dy * dy > moveCancelThreshold * moveCancelThreshold) {
                cancelPendingLongPress();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (resizeView != null) return handleResizeTouch(ev);
        if (proxy != null) {
            proxy.dragEvent(ev);
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) proxy = null;
            return true;
        }
        if (draggingView != null) {
            dragEvent(ev);
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                trackLongPressDrift(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                break;
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
            int left = leftOf(lp);
            int top = topOf(lp);
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
        /** Free-form position as page fractions; negative means "use col/row". */
        public float fx = -1f, fy = -1f;

        public GridLayoutParams(int col, int row, int colSpan, int rowSpan) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            this.col = col;
            this.row = row;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }
    }
}
