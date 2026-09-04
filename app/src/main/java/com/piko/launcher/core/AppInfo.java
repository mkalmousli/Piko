package com.piko.launcher.core;

/**
 * Immutable descriptor of a launchable activity. Icons are not held here —
 * {@link IconCache} owns bitmap lifecycles so this stays tiny and index-friendly.
 */
public final class AppInfo {

    public final String packageName;
    public final String activityName;
    public final String label;
    public final String labelLower;

    public AppInfo(String packageName, String activityName, String label) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.labelLower = label == null ? "" : label.toLowerCase();
    }

    public String key() {
        return packageName + "/" + activityName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppInfo)) return false;
        return key().equals(((AppInfo) o).key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
