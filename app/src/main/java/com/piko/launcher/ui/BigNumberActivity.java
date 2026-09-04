package com.piko.launcher.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.piko.launcher.R;
import com.piko.launcher.util.IntentUtil;

/**
 * Full-screen, huge-text rendering of a contact's phone number — meant to be
 * held up to someone else's camera/eyes across a room. Tap anywhere to copy,
 * back/tap outside dismisses.
 */
public final class BigNumberActivity extends Activity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_NUMBER = "number";

    public static void start(Context context, String name, String number) {
        Intent intent = new Intent(context, BigNumberActivity.class);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_NUMBER, number);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String number = getIntent().getStringExtra(EXTRA_NUMBER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        root.setPadding(48, 48, 48, 48);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFAAAAAA);
        nameView.setTextSize(24);
        nameView.setGravity(Gravity.CENTER);

        TextView numberView = new TextView(this);
        numberView.setText(number);
        numberView.setTextColor(0xFFFFFFFF);
        numberView.setTextSize(56);
        numberView.setGravity(Gravity.CENTER);

        root.addView(nameView);
        root.addView(numberView);
        setContentView(root);

        root.setOnClickListener(v -> {
            IntentUtil.copyToClipboard(this, "number", number);
            finish();
        });
    }
}
