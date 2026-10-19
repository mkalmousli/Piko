package com.piko.launcher.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.Background;
import com.piko.launcher.core.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Choose the home background: default, solid colour, gradient, system wallpaper or an image. */
public final class BackgroundActivity extends Activity {

    private static final int REQ_IMAGE = 5301;
    private static final int[] PALETTE = {
            0xFF0E1015, 0xFF000000, 0xFF1B1F2A, 0xFF2A3A7A, 0xFF4A2A7A, 0xFF7A2A4A,
            0xFF2A6A5A, 0xFF1F6F3F, 0xFF8A5A1F, 0xFF9A3A2A, 0xFF4A4F5A, 0xFFE8EAF0};
    private static final String[] MODES = {"Default", "Solid colour", "Gradient", "System wallpaper", "Image"};

    private Prefs prefs;
    private int mode, c1, c2, angle, dim;
    private String image;
    private LinearLayout host;
    private View preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        mode = prefs.bgMode();
        c1 = prefs.bgColor1();
        c2 = prefs.bgColor2();
        angle = prefs.bgAngle();
        dim = prefs.bgDim();
        image = prefs.bgImage();
        build();
    }

    private void commit() {
        prefs.setBackground(mode, c1, c2, angle, image, dim);
        prefs.bumpLayoutRevision();
        if (preview != null) preview.setBackground(Background.build(prefs));
    }

    private void build() {
        SettingsUi ui = new SettingsUi(this, "Background");
        host = ui.root;
        host.removeAllViews();

        preview = new View(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, ui.dp(120));
        plp.setMargins(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        host.addView(preview, plp);
        preview.setBackground(previewDrawable());

        ui.section("Type");
        for (int i = 0; i < MODES.length; i++) {
            final int m = i;
            ui.nav((mode == i ? "\u2713  " : "     ") + MODES[i], null, v -> {
                mode = m;
                if (m == Prefs.BG_IMAGE && image == null) {
                    pickImage();
                    return;
                }
                commit();
                build();
            });
        }

        if (mode == Prefs.BG_COLOR || mode == Prefs.BG_GRADIENT) {
            ui.section(mode == Prefs.BG_COLOR ? "Colour" : "Start colour");
            host.addView(swatches(c1, c -> { c1 = c; commit(); build(); }, ui));
            ui.nav("Custom hex", hex(c1), v -> askHex(c1, c -> { c1 = c; commit(); build(); }));
        }
        if (mode == Prefs.BG_GRADIENT) {
            ui.section("End colour");
            host.addView(swatches(c2, c -> { c2 = c; commit(); build(); }, ui));
            ui.nav("Custom hex", hex(c2), v -> askHex(c2, c -> { c2 = c; commit(); build(); }));
            ui.section("Direction");
            String[] dirs = {"Top to bottom", "Diagonal", "Left to right", "Diagonal (reverse)"};
            for (int i = 0; i < dirs.length; i++) {
                final int a = i;
                ui.nav((angle == i ? "\u2713  " : "     ") + dirs[i], null, v -> { angle = a; commit(); build(); });
            }
        }
        if (mode == Prefs.BG_WALLPAPER) {
            ui.hint("Shows your system wallpaper behind the launcher.");
            ui.nav("Change system wallpaper", null, v -> {
                try {
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (mode == Prefs.BG_IMAGE) {
            ui.nav("Choose image", image == null ? null : "Change", v -> pickImage());
        }
        if (mode != Prefs.BG_DEFAULT) {
            ui.section("Darken");
            TextView label = ui.text(dim + "%", 14, R.color.piko_text_secondary);
            label.setPadding(ui.dp(16), 0, ui.dp(16), 0);
            host.addView(label);
            SeekBar bar = new SeekBar(this);
            bar.setMax(70);
            bar.setProgress(dim);
            bar.setPadding(ui.dp(24), ui.dp(4), ui.dp(24), ui.dp(12));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                    dim = p;
                    label.setText(p + "%");
                    if (preview != null) preview.setBackground(previewDrawable());
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) { commit(); }
            });
            host.addView(bar);
        }
    }

    private android.graphics.drawable.Drawable previewDrawable() {
        android.graphics.drawable.Drawable d = Background.build(prefs);
        if (d == null) return new android.graphics.drawable.ColorDrawable(getResources().getColor(R.color.piko_bg));
        if (mode == Prefs.BG_WALLPAPER) return new android.graphics.drawable.ColorDrawable(0xFF555B6A);
        return d;
    }

    private interface ColorCb { void on(int color); }

    private View swatches(int selected, ColorCb cb, SettingsUi ui) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4));
        int perRow = 6;
        for (int r = 0; r < PALETTE.length / perRow; r++) {
            LinearLayout line = new LinearLayout(this);
            for (int i = 0; i < perRow; i++) {
                final int color = PALETTE[r * perRow + i];
                View sw = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(color);
                g.setStroke(ui.dp(color == selected ? 3 : 1), color == selected ? 0xFF7C9CFF : 0x55FFFFFF);
                sw.setBackground(g);
                sw.setOnClickListener(v -> cb.on(color));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(40), 1f);
                lp.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
                line.addView(sw, lp);
            }
            row.addView(line);
        }
        return row;
    }

    private static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    private void askHex(int current, ColorCb cb) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(hex(current));
        new AlertDialog.Builder(this)
                .setTitle("Hex colour")
                .setView(input)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    try {
                        String t = input.getText().toString().trim();
                        cb.on(Color.parseColor(t.startsWith("#") ? t : "#" + t) | 0xFF000000);
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, "Invalid colour", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void pickImage() {
        startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE), REQ_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMAGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (image == null) mode = prefs.bgMode();
            build();
            return;
        }
        String path = saveImage(data.getData());
        if (path == null) {
            Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        image = path;
        mode = Prefs.BG_IMAGE;
        commit();
        build();
    }

    /** Centre-crops to the screen's aspect ratio and stores it privately, so it fills the window exactly. */
    private String saveImage(Uri uri) {
        try {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, Math.min(bounds.outWidth / sw, bounds.outHeight / sh));
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                src = BitmapFactory.decodeStream(in, null, opts);
            }
            if (src == null) return null;
            float target = sw / (float) sh;
            int w = src.getWidth(), h = src.getHeight();
            int cw = w, ch = h;
            if (w / (float) h > target) cw = Math.round(h * target); else ch = Math.round(w / target);
            Bitmap crop = Bitmap.createBitmap(src, (w - cw) / 2, (h - ch) / 2, cw, ch);
            Bitmap out = cw > sw ? Bitmap.createScaledBitmap(crop, sw, sh, true) : crop;
            File dir = new File(getFilesDir(), "backgrounds");
            dir.mkdirs();
            File file = new File(dir, "bg_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream os = new FileOutputStream(file)) {
                out.compress(Bitmap.CompressFormat.JPEG, 92, os);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
