package com.piko.launcher.core;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.WindowManager;

/** Builds the home background (solid, gradient, system wallpaper or image) from {@link Prefs}. */
public final class Background {

    private Background() {}

    public static Drawable build(Prefs prefs) {
        Drawable base;
        switch (prefs.bgMode()) {
            case Prefs.BG_COLOR:
                base = new ColorDrawable(prefs.bgColor1());
                break;
            case Prefs.BG_GRADIENT:
                base = new GradientDrawable(orientation(prefs.bgAngle()), new int[]{prefs.bgColor1(), prefs.bgColor2()});
                break;
            case Prefs.BG_IMAGE: {
                Bitmap b = prefs.bgImage() == null ? null : BitmapFactory.decodeFile(prefs.bgImage());
                base = b == null ? new ColorDrawable(prefs.bgColor1()) : new BitmapDrawable(null, b);
                break;
            }
            case Prefs.BG_WALLPAPER:
                base = new ColorDrawable(Color.TRANSPARENT);
                break;
            default:
                return null;
        }
        int dim = prefs.bgDim();
        if (dim <= 0) return base;
        return new LayerDrawable(new Drawable[]{base, new ColorDrawable(Color.argb(dim * 255 / 100, 0, 0, 0))});
    }

    private static GradientDrawable.Orientation orientation(int angle) {
        switch (angle) {
            case 1: return GradientDrawable.Orientation.TL_BR;
            case 2: return GradientDrawable.Orientation.LEFT_RIGHT;
            case 3: return GradientDrawable.Orientation.BL_TR;
            default: return GradientDrawable.Orientation.TOP_BOTTOM;
        }
    }

    /** Applies the configured background to the activity window and root view. */
    public static void apply(Activity activity, View root, Prefs prefs) {
        Drawable bg = build(prefs);
        if (bg == null) return;
        boolean wallpaper = prefs.bgMode() == Prefs.BG_WALLPAPER;
        if (wallpaper) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        root.setBackground(bg);
    }
}
