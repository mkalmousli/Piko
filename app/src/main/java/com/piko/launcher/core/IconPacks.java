package com.piko.launcher.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovers ADW/Nova/Apex-style icon packs and resolves per-app icons from their appfilter.xml. */
public final class IconPacks {

    public static final class Pack {
        public final String packageName;
        public final String label;

        Pack(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static final String[] ACTIONS = {
            "org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.fede.launcher.THEME_ICONPACK", "com.anddoes.launcher.THEME"};

    private static IconPacks instance;

    private final Context context;
    private String loadedPack;
    private Resources packResources;
    private Map<String, String> mapping = new HashMap<>();

    private IconPacks(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized IconPacks get(Context context) {
        if (instance == null) instance = new IconPacks(context);
        return instance;
    }

    public List<Pack> installed() {
        PackageManager pm = context.getPackageManager();
        Map<String, Pack> found = new LinkedHashMap<>();
        for (String action : ACTIONS) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(action), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!found.containsKey(pkg)) found.put(pkg, new Pack(pkg, String.valueOf(ri.loadLabel(pm))));
            }
        }
        return new ArrayList<>(found.values());
    }

    /** The pack's drawable for a component, or null when no pack is selected or it doesn't theme this app. */
    public synchronized Drawable iconFor(String pkg, String activity) {
        String selected = Prefs.get(context).iconPack();
        if (selected.isEmpty()) return null;
        if (!selected.equals(loadedPack)) load(selected);
        if (packResources == null) return null;
        String name = mapping.get(pkg + "/" + activity);
        if (name == null) name = mapping.get(pkg + "/");
        if (name == null) return null;
        int id = packResources.getIdentifier(name, "drawable", selected);
        if (id == 0) return null;
        try {
            return packResources.getDrawable(id, null);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private void load(String pack) {
        loadedPack = pack;
        packResources = null;
        mapping = new HashMap<>();
        try {
            packResources = context.getPackageManager().getResourcesForApplication(pack);
            XmlPullParser xml = null;
            int xmlId = packResources.getIdentifier("appfilter", "xml", pack);
            if (xmlId != 0) {
                xml = packResources.getXml(xmlId);
            } else {
                try (InputStream in = packResources.getAssets().open("appfilter.xml")) {
                    xml = XmlPullParserFactory.newInstance().newPullParser();
                    xml.setInput(in, null);
                    parse(xml);
                    return;
                }
            }
            parse(xml);
        } catch (Exception e) {
            packResources = null;
        }
    }

    private void parse(XmlPullParser xml) throws Exception {
        for (int ev = xml.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = xml.next()) {
            if (ev != XmlPullParser.START_TAG || !"item".equals(xml.getName())) continue;
            String component = xml.getAttributeValue(null, "component");
            String drawable = xml.getAttributeValue(null, "drawable");
            if (component == null || drawable == null) continue;
            // Format: ComponentInfo{com.pkg/com.pkg.Activity}
            int open = component.indexOf('{'), close = component.lastIndexOf('}');
            if (open < 0 || close < open) continue;
            String ci = component.substring(open + 1, close);
            int slash = ci.indexOf('/');
            if (slash < 0) continue;
            String pkg = ci.substring(0, slash), act = ci.substring(slash + 1);
            if (act.startsWith(".")) act = pkg + act;
            mapping.put(pkg + "/" + act, drawable);
            if (!mapping.containsKey(pkg + "/")) mapping.put(pkg + "/", drawable);
        }
    }
}
