package com.piko.launcher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ranks {@link AppInfo}/{@link ContactInfo} entries against a query. Prefix
 * and word-boundary matches dominate, a subsequence fallback catches fuzzy
 * typing, multi-word queries require every word to match somewhere, usage
 * count breaks ties, and {@link SearchLearningStore} gives a dominant boost
 * to whatever the user has actually picked for this exact query before —
 * so the ranking keeps improving with use instead of staying static.
 */
public final class SearchEngine {

    private static final int SCORE_EXACT = 1000;
    private static final int SCORE_PREFIX = 800;
    private static final int SCORE_WORD_PREFIX = 650;
    private static final int SCORE_INITIALS = 500;
    private static final int SCORE_CONTAINS = 350;
    private static final int SCORE_FUZZY_BASE = 150;
    private static final int SCORE_CONTACT_BIAS = 20;
    private static final int LEARNING_BOOST_WEIGHT = 3000;
    private static final int NO_MATCH = Integer.MIN_VALUE;

    private final UsageStore usageStore;
    private final SearchLearningStore learningStore;

    public SearchEngine(UsageStore usageStore, SearchLearningStore learningStore) {
        this.usageStore = usageStore;
        this.learningStore = learningStore;
    }

    /** App-only listing (app picker dialogs). Empty query returns all apps, frecency-sorted. */
    public List<AppInfo> search(List<AppInfo> source, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        List<AppInfo> result = new ArrayList<>(source.size());

        if (query.isEmpty()) {
            result.addAll(source);
            Collections.sort(result, (a, b) -> {
                int ua = usageStore.count(a.key());
                int ub = usageStore.count(b.key());
                if (ua != ub) return ub - ua;
                return a.labelLower.compareTo(b.labelLower);
            });
            return result;
        }

        List<Scored> scored = new ArrayList<>();
        for (AppInfo app : source) {
            int score = score(app.labelLower, query);
            if (score != NO_MATCH) {
                score += Math.min(usageStore.count(app.key()), 20) * 2;
                score += learningStore.boostFor(query, app.key()) * LEARNING_BOOST_WEIGHT;
                scored.add(new Scored(app, score));
            }
        }
        Collections.sort(scored, (a, b) -> b.score - a.score);
        for (Scored s : scored) result.add(s.app);
        return result;
    }

    /**
     * Ranks apps and contacts together into one relevance-sorted list.
     * Empty query returns nothing — Piko's home page starts blank and only
     * fills in once the user actually starts typing.
     */
    public List<SearchResult> searchAll(List<AppInfo> apps, List<ContactInfo> contactsSource, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        List<SearchResult> result = new ArrayList<>();
        if (query.isEmpty()) return result;

        List<ScoredResult> scored = new ArrayList<>();
        for (AppInfo app : apps) {
            int s = score(app.labelLower, query);
            if (s != NO_MATCH) {
                s += Math.min(usageStore.count(app.key()), 20) * 2;
                s += learningStore.boostFor(query, app.key()) * LEARNING_BOOST_WEIGHT;
                scored.add(new ScoredResult(SearchResult.forApp(app), s));
            }
        }
        String queryDigits = query.replaceAll("[^0-9]", "");
        for (ContactInfo contact : contactsSource) {
            int s = score(contact.nameLower, query);
            if (s == NO_MATCH && !queryDigits.isEmpty()) {
                String numberDigits = contact.phoneNumber.replaceAll("[^0-9]", "");
                if (!numberDigits.isEmpty() && numberDigits.contains(queryDigits)) {
                    s = SCORE_CONTAINS;
                }
            }
            if (s != NO_MATCH) {
                s += learningStore.boostFor(query, contactKey(contact)) * LEARNING_BOOST_WEIGHT;
                scored.add(new ScoredResult(SearchResult.forContact(contact), s - SCORE_CONTACT_BIAS));
            }
        }
        Collections.sort(scored, (a, b) -> b.score - a.score);
        for (ScoredResult s : scored) result.add(s.result);
        return result;
    }

    /** Call once the user actually acts on a result, so this exact query favours it next time. */
    public void recordChoice(String rawQuery, String itemKey) {
        learningStore.recordSelection(rawQuery, itemKey);
    }

    public static String contactKey(ContactInfo contact) {
        return "contact:" + contact.contactId;
    }

    private int score(String label, String rawQuery) {
        String query = rawQuery.trim();
        String[] queryWords = query.split("\\s+");
        if (queryWords.length <= 1) return scoreSingle(label, query);

        // Multi-word query: every word must match somewhere in the label.
        int total = 0;
        for (String word : queryWords) {
            if (word.isEmpty()) continue;
            int s = scoreSingle(label, word);
            if (s == NO_MATCH) return NO_MATCH;
            total += s;
        }
        return total / queryWords.length;
    }

    private int scoreSingle(String label, String query) {
        if (query.isEmpty()) return NO_MATCH;
        if (label.equals(query)) return SCORE_EXACT;
        if (label.startsWith(query)) return SCORE_PREFIX - lengthPenalty(label, query);

        String[] words = label.split("[\\s\\-_.]+");
        for (String word : words) {
            if (word.startsWith(query)) return SCORE_WORD_PREFIX - lengthPenalty(label, query);
        }

        if (matchesInitials(words, query)) return SCORE_INITIALS;

        if (label.contains(query)) return SCORE_CONTAINS - lengthPenalty(label, query);

        int fuzzy = fuzzySubsequenceScore(label, query);
        if (fuzzy > 0) return SCORE_FUZZY_BASE + fuzzy - lengthPenalty(label, query);

        return NO_MATCH;
    }

    private boolean matchesInitials(String[] words, String query) {
        if (query.length() < 2 || query.length() > words.length) return false;
        int qi = 0;
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (qi >= query.length()) break;
            if (word.charAt(0) != query.charAt(qi)) return false;
            qi++;
        }
        return qi == query.length();
    }

    /** All query chars appear in label, in order, but not necessarily contiguous. */
    private int fuzzySubsequenceScore(String label, String query) {
        int li = 0, qi = 0, streak = 0, bestStreak = 0;
        while (li < label.length() && qi < query.length()) {
            if (label.charAt(li) == query.charAt(qi)) {
                qi++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
            } else {
                streak = 0;
            }
            li++;
        }
        if (qi < query.length()) return 0;
        return bestStreak * 10;
    }

    private int lengthPenalty(String label, String query) {
        return Math.min(label.length() - query.length(), 50);
    }

    private static final class Scored {
        final AppInfo app;
        final int score;

        Scored(AppInfo app, int score) {
            this.app = app;
            this.score = score;
        }
    }

    private static final class ScoredResult {
        final SearchResult result;
        final int score;

        ScoredResult(SearchResult result, int score) {
            this.result = result;
            this.score = score;
        }
    }
}
