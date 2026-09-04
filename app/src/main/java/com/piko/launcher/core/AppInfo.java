package com.piko.launcher.core;

/**
 * Descriptor of a launchable activity, after any user customization
 * ({@link AppCustomizationStore}) has been applied: {@link #label} may be a
 * user-chosen rename, {@link #tags} are extra search-only keywords baked
 * into {@link #labelLower}, and {@link #customIconPath} points at a
 * user-picked icon file when set. Icons themselves are not held here —
 * {@link IconCache} owns bitmap lifecycles so this stays tiny and index-friendly.
 */
public final class AppInfo {

    public final String packageName;
    public final String activityName;
    public final String label;
    public final String tags;
    public final String labelLower;
    public final String customIconPath;

    public AppInfo(String packageName, String activityName, String label) {
        this(packageName, activityName, label, "", null);
    }

    public AppInfo(String packageName, String activityName, String label, String tags, String customIconPath) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label == null ? "" : label;
        this.tags = tags == null ? "" : tags;
        this.labelLower = (this.label + " " + this.tags).toLowerCase().trim();
        this.customIconPath = customIconPath;
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
