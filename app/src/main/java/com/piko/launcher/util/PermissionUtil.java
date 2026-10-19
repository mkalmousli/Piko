package com.piko.launcher.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

public final class PermissionUtil {

    public static final int REQ_CONTACTS = 501;
    public static final int REQ_CALL_PHONE = 502;

    private PermissionUtil() {
    }

    public static boolean has(Context context, String permission) {
        return android.os.Build.VERSION.SDK_INT < 23
                || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestContacts(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    public static void requestCallPhone(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            activity.requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PHONE);
        }
    }
}
