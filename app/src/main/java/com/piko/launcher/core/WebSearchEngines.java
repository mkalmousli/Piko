package com.piko.launcher.core;

import java.util.Arrays;
import java.util.List;

/** Fixed catalog of web search engines Piko can fall back to; enablement/order lives in {@link Prefs}. */
public final class WebSearchEngines {

    public static final class Engine {
        public final String id;
        public final String label;
        public final String urlPrefix;

        public Engine(String id, String label, String urlPrefix) {
            this.id = id;
            this.label = label;
            this.urlPrefix = urlPrefix;
        }

        /** A "%s" in the template is replaced by the query; otherwise the query is appended. */
        public String urlFor(String query) {
            String encoded = android.net.Uri.encode(query);
            return urlPrefix.contains("%s") ? urlPrefix.replace("%s", encoded) : urlPrefix + encoded;
        }
    }

    public static final List<Engine> ALL = Arrays.asList(
            new Engine("google", "Google", "https://www.google.com/search?q="),
            new Engine("brave", "Brave", "https://search.brave.com/search?q="),
            new Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
            new Engine("bing", "Bing", "https://www.bing.com/search?q="),
            new Engine("chatgpt", "ChatGPT", "https://chatgpt.com/?q="),
            new Engine("claude", "Claude", "https://claude.ai/new?q="),
            new Engine("perplexity", "Perplexity", "https://www.perplexity.ai/search?q=")
    );

    private WebSearchEngines() {
    }

    /** Built-in catalog lookup only; user-defined engines resolve through {@link Prefs#engineById}. */
    public static Engine builtIn(String id) {
        for (Engine e : ALL) if (e.id.equals(id)) return e;
        return null;
    }
}
