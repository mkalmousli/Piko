package com.piko.launcher.core;

/**
 * Recursive-descent evaluator for the inline calculator: + - * / ^ % mod,
 * parentheses, implicit multiplication ("2(3+4)", "2pi"), postfix ! and %,
 * constants (pi, e) and functions (sqrt, cbrt, sin, cos, tan, asin, acos,
 * atan, ln, log, log2, exp, abs, floor, ceil, round). Trig uses radians
 * unless the argument carries a "deg" suffix. Returns null for anything
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
        if (q.isEmpty() || !q.matches("[0-9a-zA-Z+\\-*/^().,%!×÷√\\s]+")) return false;
        // A bare number counts too, so typing digits always shows the calculator row.
        return q.matches(".*[0-9].*") || q.matches("(?i).*\\b(pi|e)\\b.*[-+*/^].*");
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
        if (Double.isNaN(value) || Double.isInfinite(value)) return String.valueOf(value);
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        java.math.BigDecimal bd = new java.math.BigDecimal(value, new java.math.MathContext(12));
        String out = Math.abs(value) >= 1e15 || Math.abs(value) < 1e-6
                ? bd.stripTrailingZeros().toString() : bd.stripTrailingZeros().toPlainString();
        return out;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s.replace('×', '*').replace('÷', '/').replace("√", "sqrt").toLowerCase(java.util.Locale.ROOT);
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private boolean eat(char c) {
            skipSpaces();
            if (!atEnd() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private boolean eatWord(String w) {
            skipSpaces();
            if (s.startsWith(w, pos) && (pos + w.length() >= s.length() || !Character.isLetter(s.charAt(pos + w.length())))) {
                pos += w.length();
                return true;
            }
            return false;
        }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (eat('+')) value += parseTerm();
                else if (eat('-')) value -= parseTerm();
                else break;
            }
            return value;
        }

        double parseTerm() {
            double value = parseUnary();
            while (true) {
                if (eat('*')) value *= parseUnary();
                else if (eat('/')) {
                    double d = parseUnary();
                    if (d == 0) throw new ArithmeticException("div0");
                    value /= d;
                } else if (eatWord("mod")) {
                    double d = parseUnary();
                    if (d == 0) throw new ArithmeticException("div0");
                    value %= d;
                } else if (eatWord("of")) {
                    value *= parseUnary();
                } else if (startsOperand()) {
                    value *= parseUnary();   // implicit multiplication
                } else break;
            }
            return value;
        }

        private boolean startsOperand() {
            skipSpaces();
            if (atEnd()) return false;
            char c = s.charAt(pos);
            return c == '(' || Character.isDigit(c) || c == '.' || Character.isLetter(c);
        }

        double parseUnary() {
            if (eat('-')) return -parseUnary();
            if (eat('+')) return parseUnary();
            return parsePower();
        }

        double parsePower() {
            double base = parsePostfix();
            if (eat('^')) return Math.pow(base, parseUnary());   // right associative
            return base;
        }

        double parsePostfix() {
            double v = parsePrimary();
            while (true) {
                skipSpaces();
                if (!atEnd() && s.charAt(pos) == '!') {
                    pos++;
                    v = factorial(v);
                } else if (!atEnd() && s.charAt(pos) == '%') {
                    pos++;
                    v /= 100.0;
                } else break;
            }
            return v;
        }

        private double factorial(double v) {
            if (v < 0 || v != Math.rint(v) || v > 170) throw new ArithmeticException("fact");
            double r = 1;
            for (int i = 2; i <= (int) v; i++) r *= i;
            return r;
        }

        double parsePrimary() {
            skipSpaces();
            if (atEnd()) throw new IllegalStateException("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double value = parseExpression();
                if (!eat(')')) throw new IllegalStateException("missing )");
                return value;
            }
            if (c == '|') {
                pos++;
                double value = Math.abs(parseExpression());
                if (!eat('|')) throw new IllegalStateException("missing |");
                return value;
            }
            if (Character.isLetter(c)) return parseName();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.' || s.charAt(pos) == ',')) pos++;
            if (start == pos) throw new IllegalStateException("expected number");
            double n = Double.parseDouble(s.substring(start, pos).replace(",", ""));
            if (pos < s.length() && s.charAt(pos) == 'e' && pos + 1 < s.length()
                    && (Character.isDigit(s.charAt(pos + 1)) || ((s.charAt(pos + 1) == '-' || s.charAt(pos + 1) == '+')
                    && pos + 2 < s.length() && Character.isDigit(s.charAt(pos + 2))))) {
                int save = pos;
                pos++;
                int es = pos;
                if (s.charAt(pos) == '-' || s.charAt(pos) == '+') pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                n *= Math.pow(10, Double.parseDouble(s.substring(es, pos)));
            }
            return n;
        }

        private double parseName() {
            int start = pos;
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
            if (s.startsWith("log", start) && pos - start == 3 && pos < s.length() && s.charAt(pos) == '2') pos++;
            String name = s.substring(start, pos);
            if (name.equals("pi")) return Math.PI;
            if (name.equals("e")) return Math.E;
            if (name.equals("tau")) return 2 * Math.PI;
            if (name.equals("deg")) return Math.PI / 180;   // "30 deg" → radians via implicit multiplication
            double arg;
            skipSpaces();
            if (!atEnd() && s.charAt(pos) == '(') {
                pos++;
                arg = parseExpression();
                if (!eat(')')) throw new IllegalStateException("missing )");
            } else {
                arg = parseUnary();
            }
            switch (name) {
                case "sqrt": if (arg < 0) throw new ArithmeticException("sqrt"); return Math.sqrt(arg);
                case "cbrt": return Math.cbrt(arg);
                case "sin": return Math.sin(arg);
                case "cos": return Math.cos(arg);
                case "tan": return Math.tan(arg);
                case "asin": return Math.asin(arg);
                case "acos": return Math.acos(arg);
                case "atan": return Math.atan(arg);
                case "ln": return Math.log(arg);
                case "log": return Math.log10(arg);
                case "log2": return Math.log(arg) / Math.log(2);
                case "exp": return Math.exp(arg);
                case "abs": return Math.abs(arg);
                case "floor": return Math.floor(arg);
                case "ceil": return Math.ceil(arg);
                case "round": return Math.rint(arg);
                default: throw new IllegalStateException("unknown " + name);
            }
        }
    }
}
