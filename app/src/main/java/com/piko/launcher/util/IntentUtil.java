package com.piko.launcher.util;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.widget.Toast;

import com.piko.launcher.R;
import com.piko.launcher.core.AppInfo;
import com.piko.launcher.core.WebSearchEngines;

/**
 * Every outward-facing Intent Piko fires lives here: app launch/management,
 * dialer, and the WhatsApp shortcuts. WhatsApp only documents chat deep
 * links (wa.me); voice/video use a widely relied-upon extra-based intent
 * that several third-party launchers ship, with a graceful fallback to the
 * chat screen if the installed WhatsApp build doesn't honour it.
 */
public final class IntentUtil {

    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    private IntentUtil() {
    }

    public static void launchApp(Context context, AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_results, Toast.LENGTH_SHORT).show();
        }
    }

    public static void openAppInfo(Context context, String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void uninstall(Context context, String packageName) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Some OEM package installers reject ACTION_DELETE outright (or the app is a
            // protected system package); ACTION_UNINSTALL_PACKAGE is the documented fallback.
            try {
                Intent fallback = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + packageName));
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception e2) {
                Toast.makeText(context, R.string.uninstall_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Opens the dialer pre-filled; never requires a runtime permission. */
    public static void dial(Context context, String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNumber)));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Places the call directly. Caller must already hold CALL_PHONE. */
    public static void callDirect(Context context, String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(phoneNumber)));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** Opens the default SMS app with a conversation pre-selected; no permission required. */
    public static void sendSms(Context context, String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phoneNumber)));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_results, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isWhatsAppInstalled(Context context) {
        return resolveWhatsAppPackage(context) != null;
    }

    public static void whatsAppMessage(Context context, String phoneNumber) {
        String pkg = resolveWhatsAppPackage(context);
        if (pkg == null) {
            Toast.makeText(context, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        String digits = onlyDigits(phoneNumber);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits));
        Compat.setPackage(intent, pkg);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Compat.setPackage(intent, null);
            context.startActivity(intent);
        }
    }

    public static void whatsAppCall(Context context, String phoneNumber, boolean video) {
        String pkg = resolveWhatsAppPackage(context);
        if (pkg == null) {
            Toast.makeText(context, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        String jid = onlyDigits(phoneNumber) + "@s.whatsapp.net";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Compat.setPackage(intent, pkg);
        intent.setClassName(pkg, pkg + ".voipcalling.VoipActivity");
        intent.putExtra("jid", jid);
        intent.putExtra("call_type", video ? "video" : "voice");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Undocumented activity not present on this WhatsApp build: fall back to chat.
            whatsAppMessage(context, phoneNumber);
        }
    }

    private static String resolveWhatsAppPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        if (isInstalled(pm, WHATSAPP_PACKAGE)) return WHATSAPP_PACKAGE;
        if (isInstalled(pm, WHATSAPP_BUSINESS_PACKAGE)) return WHATSAPP_BUSINESS_PACKAGE;
        return null;
    }

    private static boolean isInstalled(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Opens the given engine id (see {@link WebSearchEngines}) with the query pre-filled. */
    public static void webSearch(Context context, String engineId, String query) {
        String url = com.piko.launcher.core.Prefs.get(context).engineById(engineId).urlFor(query);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_results, Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the system Contacts app on this contact's detail page. */
    public static void openContactInfo(Context context, long contactId) {
        if (android.os.Build.VERSION.SDK_INT < 5) return;
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_results, Toast.LENGTH_SHORT).show();
        }
    }

    public static void copyToClipboard(Context context, String label, String text) {
        com.piko.launcher.util.Compat.copyToClipboard(context, label, text);
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private static String onlyDigits(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9+]", "").replaceFirst("^\\+", "");
    }
}
