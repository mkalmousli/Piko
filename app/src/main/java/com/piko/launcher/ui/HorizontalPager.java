package com.piko.launcher.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

/**
 * Minimal, dependency-free page swiper (the platform has no ViewPager —
 * that lives in AndroidX). Children are laid out edge to edge; horizontal
 * drags page between them while vertical drags and taps fall through to
 * child views untouched.
 */
public final class HorizontalPager extends ViewGroup {

    public interface OnPageChangeListener {
        void onPageSelected(int index);
    }

    private final Scroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private VelocityTracker velocityTracker;
    private int currentItem = 0;
    private float downX, downY, lastX;
    private boolean dragging = false;
    private OnPageChangeListener listener;
    private boolean tilt3d = true;

    public HorizontalPager(Context context) {
        super(context);
        scroller = new Scroller(context, new DecelerateInterpolator(1.6f));
        touchSlop = com.piko.launcher.util.Compat.touchSlop(context);
        minFlingVelocity = com.piko.launcher.util.Compat.minFling(context);
        maxFlingVelocity = com.piko.launcher.util.Compat.maxFling(context);
        if (android.os.Build.VERSION.SDK_INT >= 7) setChildrenDrawingOrderEnabled(false);
    }

    public void setOnPageChangeListener(OnPageChangeListener l) {
        this.listener = l;
    }

    public int getCurrentItem() {
        return currentItem;
    }

    public void setCurrentItem(int index, boolean smooth) {
        index = Math.max(0, Math.min(index, getChildCount() - 1));
        int targetX = index * getWidth();
        if (getWidth() == 0) {
            currentItem = index;
            return;
        }
        if (smooth) {
            scroller.startScroll(getScrollX(), 0, targetX - getScrollX(), 0, 320);
            com.piko.launcher.util.Compat.postInvalidate(this);
        } else {
            scrollTo(targetX, 0);
        }
        if (currentItem != index) {
            currentItem = index;
            if (listener != null) listener.onPageSelected(currentItem);
        }
    }

    /** Pages swing like a door hinged on the page edge as they slide, giving the swipe real depth. */
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        int width = getWidth();
        if (width == 0) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            float offset = (i * width - l) / (float) width;
            if (!tilt3d || Math.abs(offset) >= 1f || offset == 0f) {
                com.piko.launcher.util.Compat.setRotationY(child, 0f);
                com.piko.launcher.util.Compat.setAlpha(child, 1f);
                com.piko.launcher.util.Compat.setTranslationX(child, 0f);
                continue;
            }
            com.piko.launcher.util.Compat.setCameraDistance(child, 10000 * density);
            com.piko.launcher.util.Compat.setPivot(child, offset > 0 ? 0f : width, getHeight() / 2f);
            com.piko.launcher.util.Compat.setRotationY(child, -offset * 28f);
            com.piko.launcher.util.Compat.setAlpha(child, 1f - Math.abs(offset) * 0.35f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int left = i * width;
            child.layout(left, 0, left + width, b - t);
        }
        scrollTo(currentItem * width, 0);
    }

    private boolean swipeEnabled = true;

    public void setSwipeEnabled(boolean enabled) {
        swipeEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) return false;
        switch (com.piko.launcher.util.Compat.actionMasked(ev)) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                if (!scroller.isFinished()) scroller.abortAnimation();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    lastX = ev.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);

        switch (com.piko.launcher.util.Compat.actionMasked(ev)) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                if (!scroller.isFinished()) scroller.abortAnimation();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                if (!dragging && Math.abs(dx) > touchSlop) dragging = true;
                if (dragging) {
                    float delta = lastX - ev.getX();
                    lastX = ev.getX();
                    int max = (getChildCount() - 1) * getWidth();
                    int sx = getScrollX();
                    if (sx < 0 || sx > max) delta *= 0.35f;  // rubber-band past the first/last page
                    scrollBy((int) delta, 0);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    if (android.os.Build.VERSION.SDK_INT >= 4) velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    else velocityTracker.computeCurrentVelocity(1000);
                    float vx = velocityTracker.getXVelocity();
                    settle(vx);
                }
                dragging = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
            default:
                return true;
        }
    }

    private void settle(float velocityX) {
        int width = getWidth();
        if (width == 0) return;
        int page = currentItem;
        if (velocityX < -minFlingVelocity) {
            page = currentItem + 1;
        } else if (velocityX > minFlingVelocity) {
            page = currentItem - 1;
        } else {
            page = Math.round(getScrollX() / (float) width);
        }
        setCurrentItem(page, true);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            com.piko.launcher.util.Compat.postInvalidate(this);
        }
    }
}
