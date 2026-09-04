package com.piko.launcher.core;

import java.util.Arrays;
import java.util.List;

/** Fixed catalog of web search engines Piko can fall back to; enablement/order lives in {@link Prefs}. */
public final class WebSearchEngines {

    public static final class Engine {
        public final String id;
        public final String label;
        public final String urlPrefix;

        Engine(String id, String label, String urlPrefix) {
            this.id = id;
            this.label = label;
            this.urlPrefix = urlPrefix;
        }
    }

    public static final List<Engine> ALL = Arrays.asList(
            new Engine("google", "Google", "https://www.google.com/search?q="),
            new Engine("brave", "Brave", "https://search.brave.com/search?q="),
            new Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
            new Engine("bing", "Bing", "https://www.bing.com/search?q=")
    );

    private WebSearchEngines() {
    }

    public static Engine byId(String id) {
        for (Engine e : ALL) if (e.id.equals(id)) return e;
        return ALL.get(0);
    }
}
