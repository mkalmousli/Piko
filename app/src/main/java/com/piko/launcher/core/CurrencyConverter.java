package com.piko.launcher.core;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects "<amount> <CUR> to <CUR>" style search queries and resolves them
 * against frankfurter.app's free, key-less exchange-rate API on a
 * background thread.
 */
public final class CurrencyConverter {

    public interface Callback {
        void onResult(String query, double convertedValue, String toCurrency);
        void onFailure(String query);
    }

    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]{3})\\s+(?:to|in)\\s+([a-zA-Z]{3})\\s*$");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CurrencyConverter() {
    }

    public static boolean looksLikeConversion(String query) {
        return query != null && PATTERN.matcher(query.trim()).matches();
    }

    /** Fires {@code callback} on the main thread. Safe to call off the UI thread's query-changed path. */
    public static void convertAsync(String rawQuery, Callback callback) {
        Matcher m = PATTERN.matcher(rawQuery.trim());
        if (!m.matches()) {
            callback.onFailure(rawQuery);
            return;
        }
        double amount = Double.parseDouble(m.group(1));
        String from = m.group(2).toUpperCase();
        String to = m.group(3).toUpperCase();

        new Thread(() -> {
            try {
                URL url = new URL("https://api.frankfurter.app/latest?amount="
                        + amount + "&from=" + from + "&to=" + to);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                conn.disconnect();

                double value = extractRateValue(body.toString(), to);
                MAIN.post(() -> callback.onResult(rawQuery, value, to));
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure(rawQuery));
            }
        }).start();
    }

    /** Pulls the single numeric value out of {"...,"rates":{"EUR":123.45}} without a JSON dependency. */
    private static double extractRateValue(String json, String currencyCode) {
        Pattern p = Pattern.compile("\"" + currencyCode + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Double.parseDouble(m.group(1));
        throw new IllegalStateException("rate not found");
    }
}
