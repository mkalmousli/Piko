package com.piko.launcher.core;

/**
 * Tiny recursive-descent evaluator for +, -, *, /, parens and decimals, used
 * to power the search bar's inline calculator. Returns null for anything
 * that isn't a plain arithmetic expression (including empty/non-numeric
 * input), so callers can cheaply probe arbitrary search queries.
 */
public final class MathEval {

    private MathEval() {
    }

    /** Only bother evaluating queries that look like arithmetic — avoids false positives on plain text. */
    public static boolean looksLikeExpression(String query) {
        if (query == null) return false;
        String q = query.trim();
        if (q.isEmpty() || !q.matches("[0-9+\\-*/().\\s]+")) return false;
        // A bare number counts too, so typing digits always shows the calculator row.
        return q.matches(".*[0-9].*");
    }

    public static Double evaluate(String expression) {
        try {
            Parser p = new Parser(expression);
            double result = p.parseExpression();
            p.skipSpaces();
            if (!p.atEnd()) return null;
            return result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (atEnd()) break;
                char c = s.charAt(pos);
                if (c == '+') {
                    pos++;
                    value += parseTerm();
                } else if (c == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (atEnd()) break;
                char c = s.charAt(pos);
                if (c == '*') {
                    pos++;
                    value *= parseFactor();
                } else if (c == '/') {
                    pos++;
                    double divisor = parseFactor();
                    if (divisor == 0) throw new ArithmeticException("div0");
                    value /= divisor;
                } else {
                    break;
                }
            }
            return value;
        }

        double parseFactor() {
            skipSpaces();
            if (atEnd()) throw new IllegalStateException("unexpected end");
            char c = s.charAt(pos);
            if (c == '-') {
                pos++;
                return -parseFactor();
            }
            if (c == '+') {
                pos++;
                return parseFactor();
            }
            if (c == '(') {
                pos++;
                double value = parseExpression();
                skipSpaces();
                if (atEnd() || s.charAt(pos) != ')') throw new IllegalStateException("missing )");
                pos++;
                return value;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalStateException("expected number");
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
