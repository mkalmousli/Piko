package com.piko.launcher.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal stand-in for android.util.LruCache, which only exists from Android 3.1. */
public class LruCache<K, V> {
    private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true);
    private final int maxSize;
    private int size;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
    }

    protected int sizeOf(K key, V value) {
        return 1;
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized V put(K key, V value) {
        size += sizeOf(key, value);
        V old = map.put(key, value);
        if (old != null) size -= sizeOf(key, old);
        Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
        while (size > maxSize && it.hasNext()) {
            Map.Entry<K, V> eldest = it.next();
            size -= sizeOf(eldest.getKey(), eldest.getValue());
            it.remove();
        }
        return old;
    }

    public synchronized void evictAll() {
        map.clear();
        size = 0;
    }
}
