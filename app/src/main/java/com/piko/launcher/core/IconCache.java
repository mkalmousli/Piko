package com.piko.launcher.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memory-cached, size-normalised app icon loader. Icons are decoded off the
 * UI thread and delivered back only if the target ImageView still wants that
 * exact icon (list rows get recycled while a load is in flight).
 */
public final class IconCache {

    private static IconCache instance;

    private final Context appContext;
    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private IconCache(Context context) {
        this.appContext = context.getApplicationContext();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        memoryCache = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static synchronized IconCache get(Context context) {
        if (instance == null) instance = new IconCache(context);
        return instance;
    }

    public void load(AppInfo app, ImageView target, int sizePx) {
        String key = app.key() + "@" + sizePx + "~" + Prefs.get(appContext).iconPack() + (app.customIconPath != null ? "#" + app.customIconPath : "");
        target.setTag(key);

        Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageResource(com.piko.launcher.R.drawable.ic_placeholder_app);
        executor.execute(() -> {
            Bitmap bitmap = decode(app, sizePx);
            if (bitmap != null) memoryCache.put(key, bitmap);
            mainHandler.post(() -> {
                if (key.equals(target.getTag()) && bitmap != null) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    public void evict(String appKeyPrefix) {
        // Cheap-enough full sweep; icon cache is small relative to app memory.
        memoryCache.evictAll();
    }

    private Bitmap decode(AppInfo app, int sizePx) {
        if (app.customIconPath != null) {
            Bitmap custom = decodeCustomIcon(app.customIconPath, sizePx);
            if (custom != null) return custom;
        }
        try {
            Drawable packed = IconPacks.get(appContext).iconFor(app.packageName, app.activityName);
            if (packed != null) return toBitmap(packed, sizePx);
            PackageManager pm = appContext.getPackageManager();
            Drawable drawable = pm.getActivityIcon(new android.content.ComponentName(app.packageName, app.activityName));
            return toBitmap(drawable, sizePx);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeCustomIcon(String path, int sizePx) {
        Bitmap decoded = BitmapFactory.decodeFile(path);
        if (decoded == null) return null;
        return Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true);
    }

    private Bitmap toBitmap(Drawable drawable, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, sizePx, sizePx);
        drawable.draw(canvas);
        return bitmap;
    }
}
