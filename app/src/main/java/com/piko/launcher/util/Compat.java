package com.piko.launcher.util;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collection;
import java.util.Map;

/** Thin wrappers that degrade gracefully on Android versions older than the API they use. */
public final class Compat {
    private Compat() {
    }

    public static int sdk() {
        return android.os.Build.VERSION.SDK_INT;
    }

    public static boolean isEmpty(CharSequence s) {
        return s.length() == 0;
    }

    public static boolean isEmpty(Collection<?> c) {
        return c.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> m) {
        return m.isEmpty();
    }

    public static void apply(SharedPreferences.Editor editor) {
        if (android.os.Build.VERSION.SDK_INT >= 9) editor.apply();
        else editor.commit();
    }

    public static int actionMasked(MotionEvent e) {
        return e.getAction() & 0xff;
    }

    public static void haptic(View v, int constant) {
        if (android.os.Build.VERSION.SDK_INT >= 3) v.performHapticFeedback(constant);
    }

    public static void setBackground(View v, Drawable d) {
        if (android.os.Build.VERSION.SDK_INT >= 16) v.setBackground(d);
        else v.setBackgroundDrawable(d);
    }

    public static void setAlpha(View v, float a) {
        if (android.os.Build.VERSION.SDK_INT >= 11) v.setAlpha(a);
        else v.setVisibility(a <= 0f ? View.INVISIBLE : View.VISIBLE);
    }

    public static float getScaleX(View v) {
        return android.os.Build.VERSION.SDK_INT >= 11 ? v.getScaleX() : 1f;
    }

    public static void setTranslationX(View v, float x) {
        if (android.os.Build.VERSION.SDK_INT >= 11) v.setTranslationX(x);
    }

    public static void setRotationY(View v, float r) {
        if (android.os.Build.VERSION.SDK_INT >= 11) v.setRotationY(r);
    }

    public static void setCameraDistance(View v, float d) {
        if (android.os.Build.VERSION.SDK_INT >= 12) v.setCameraDistance(d);
    }

    public static void post(View v, Runnable r) {
        if (android.os.Build.VERSION.SDK_INT >= 16) v.postOnAnimation(r);
        else v.post(r);
    }

    public static void postInvalidate(View v) {
        if (android.os.Build.VERSION.SDK_INT >= 16) v.postInvalidateOnAnimation();
        else v.postInvalidate();
    }

    /** View property animation; on Android before 3.1 the end state is applied at once. */
    public static Anim animate(View v) {
        return new Anim(v);
    }

    public static final class Anim {
        private final View view;
        private Runnable end;
        private Float alpha, scaleX, scaleY, translationX, translationY, rotationX;
        private long duration = 250;
        private android.view.animation.Interpolator interpolator;

        Anim(View v) {
            view = v;
        }

        public Anim alpha(float v) {
            alpha = v;
            return this;
        }

        public Anim scaleX(float v) {
            scaleX = v;
            return this;
        }

        public Anim scaleY(float v) {
            scaleY = v;
            return this;
        }

        public Anim translationX(float v) {
            translationX = v;
            return this;
        }

        public Anim translationY(float v) {
            translationY = v;
            return this;
        }

        public Anim rotationX(float v) {
            rotationX = v;
            return this;
        }

        public Anim setDuration(long d) {
            duration = d;
            return this;
        }

        public Anim setInterpolator(android.view.animation.Interpolator i) {
            interpolator = i;
            return this;
        }

        public Anim withEndAction(Runnable r) {
            end = r;
            return this;
        }

        /** Stops a running animation on the view (no-op before Android 4.0). */
        public void cancel() {
            if (android.os.Build.VERSION.SDK_INT >= 14) view.animate().cancel();
        }

        public void start() {
            if (android.os.Build.VERSION.SDK_INT < 12) {
                if (alpha != null) setAlpha(view, alpha);
                if (end != null) end.run();
                return;
            }
            android.view.ViewPropertyAnimator a = view.animate();
            if (alpha != null) a.alpha(alpha);
            if (scaleX != null) a.scaleX(scaleX);
            if (scaleY != null) a.scaleY(scaleY);
            if (translationX != null) a.translationX(translationX);
            if (translationY != null) a.translationY(translationY);
            if (rotationX != null) a.rotationX(rotationX);
            a.setDuration(duration);
            if (interpolator != null) a.setInterpolator(interpolator);
            if (end != null) {
                if (android.os.Build.VERSION.SDK_INT >= 16) a.withEndAction(end);
                else view.postDelayed(end, duration);
            }
            if (android.os.Build.VERSION.SDK_INT >= 14) a.start();
        }
    }

    public static Drawable drawable(android.content.res.Resources r, int id) {
        return android.os.Build.VERSION.SDK_INT >= 21 ? r.getDrawable(id, null) : r.getDrawable(id);
    }

    public static int touchSlop(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT >= 3
                ? android.view.ViewConfiguration.get(c).getScaledTouchSlop() : Math.round(8 * density(c));
    }

    public static int minFling(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT >= 4
                ? android.view.ViewConfiguration.get(c).getScaledMinimumFlingVelocity() : Math.round(50 * density(c));
    }

    public static int maxFling(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT >= 4
                ? android.view.ViewConfiguration.get(c).getScaledMaximumFlingVelocity() : Math.round(4000 * density(c));
    }

    private static float density(android.content.Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    public static void tint(android.widget.ImageView v, int color) {
        v.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_ATOP);
    }

    public static void recreate(android.app.Activity a) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            a.recreate();
        } else {
            android.content.Intent i = a.getIntent();
            a.finish();
            a.startActivity(i);
        }
    }

    public static boolean isChangingConfigurations(android.app.Activity a) {
        return android.os.Build.VERSION.SDK_INT >= 11 && a.isChangingConfigurations();
    }

    public static void setScale(View v, float s) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            v.setScaleX(s);
            v.setScaleY(s);
        }
    }

    public static float getScaleY(View v) {
        return android.os.Build.VERSION.SDK_INT >= 11 ? v.getScaleY() : 1f;
    }

    public static void setTranslationY(View v, float y) {
        if (android.os.Build.VERSION.SDK_INT >= 11) v.setTranslationY(y);
    }

    public static void setRotation(View v, float r) {
        if (android.os.Build.VERSION.SDK_INT >= 11) v.setRotation(r);
    }

    public static void setPivot(View v, float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            v.setPivotX(x);
            v.setPivotY(y);
        }
    }

    public static void setElevation(View v, float e) {
        if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(e);
    }

    public static void setOverscrollNever(View v) {
        if (android.os.Build.VERSION.SDK_INT >= 9) v.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    public static void showKeyboard(View v) {
        if (android.os.Build.VERSION.SDK_INT < 3) return;
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View v) {
        if (android.os.Build.VERSION.SDK_INT < 3) return;
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static void copyToClipboard(android.content.Context c, CharSequence label, CharSequence text) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    c.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
        } else {
            android.text.ClipboardManager cm = (android.text.ClipboardManager)
                    c.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setText(text);
        }
    }

    /** A toggle: a Switch where available (Android 4.0+), else a check box. */
    public static android.widget.CompoundButton newSwitch(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT >= 14 ? new android.widget.Switch(c) : new android.widget.CheckBox(c);
    }

    public static void setPackage(android.content.Intent i, String pkg) {
        if (android.os.Build.VERSION.SDK_INT >= 4) i.setPackage(pkg);
    }

    public static String toUri(android.content.Intent i) {
        return android.os.Build.VERSION.SDK_INT >= 4 ? i.toUri(0) : i.toURI();
    }

    public static android.content.Intent parseUri(String uri) throws java.net.URISyntaxException {
        return android.os.Build.VERSION.SDK_INT >= 4 ? android.content.Intent.parseUri(uri, 0)
                : android.content.Intent.getIntent(uri);
    }

    public static Drawable bitmapDrawable(android.content.res.Resources r, android.graphics.Bitmap b) {
        return android.os.Build.VERSION.SDK_INT >= 4
                ? new android.graphics.drawable.BitmapDrawable(r, b)
                : new android.graphics.drawable.BitmapDrawable(b);
    }

    private static int nextViewId = 0x5ee00000;

    public static int generateViewId() {
        return android.os.Build.VERSION.SDK_INT >= 17 ? View.generateViewId() : nextViewId++;
    }

    public static void setInputType(android.widget.TextView v, int type) {
        if (android.os.Build.VERSION.SDK_INT >= 5) v.setInputType(type);
    }
}
