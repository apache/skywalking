package org.apache.skywalking.apm.plugin.trace.ignore.matcher;

/**
 * @author kanro
 */
public class FastPathMatcher implements TracePathMatcher {
    @Override
    public boolean match(String pattern, String path) {
        return normalMatch(pattern, 0, path, 0);
    }

    private boolean normalMatch(String pat, int p, String str, int s) {
        while (p < pat.length()) {
            char pc = pat.charAt(p);
            char sc = safeCharAt(str, s);

            // Got * in pattern, entry the wildcard mode.
            //            ↓        ↓
            // pattern: a/*      a/*
            //            ↓        ↓
            // string:  a/bcd    a/
            if (pc == '*') {
                p++;
                // Got * in pattern again, entry the multi-wildcard mode.
                //             ↓        ↓
                // pattern: a/**     a/**
                //            ↓        ↓
                // string:  a/bcd    a/
                if (safeCharAt(pat, p) == '*') {
                    p++;
                    // Entry the multi-wildcard mode.
                    //              ↓        ↓
                    // pattern: a/**     a/**
                    //            ↓        ↓
                    // string:  a/bcd    a/
                    return multiWildcardMatch(pat, p, str, s);
                } else {
                    // Entry the wildcard mode.
                    //             ↓
                    // pattern: a/*
                    //            ↓
                    // string:  a/bcd
                    return wildcardMatch(pat, p, str, s);
                }
            }

            // Matching ? for non-'/' char, or matching the same chars.
            //            ↓        ↓       ↓
            // pattern: a/?/c    a/b/c    a/b
            //            ↓        ↓       ↓
            // string:  a/b/c    a/b/d    a/d
            if ((pc == '?' && sc != 0 && sc != '/') || pc == sc) {
                s++;
                p++;
                continue;
            }

            // Not matched.
            //            ↓
            // pattern: a/b
            //            ↓
            // string:  a/c
            return false;
        }

        return s == str.length();
    }

    private boolean wildcardMatch(String pat, int p, String str, int s) {
        char pc = safeCharAt(pat, p);

        while (true) {
            char sc = safeCharAt(str, s);

            if (sc == '/') {
                // Both of pattern and string '/' matched, exit wildcard mode.
                //             ↓
                // pattern: a/*/
                //              ↓
                // string:  a/bc/
                if (pc == sc) {
                    return normalMatch(pat, p + 1, str, s + 1);
                }

                // Not matched string in current path part.
                //             ↓        ↓
                // pattern: a/*      a/*d
                //              ↓        ↓
                // string:  a/bc/    a/bc/
                return false;
            }

            // Try to entry normal mode, if not matched, increasing s and try again.
            if (!normalMatch(pat, p, str, s)) {
                // End of string, not matched.
                if (s >= str.length()) {
                    return false;
                }

                s++;
                continue;
            }

            // Matched in next normal mode.
            return true;
        }
    }

    private boolean multiWildcardMatch(String pat, int p, String str, int s) {
        // End of pattern, just check the end of string is '/' quickly.
        if (p >= pat.length() && s < str.length()) {
            return str.charAt(str.length() - 1) != '/';
        }

        while (true) {
            // Try to entry normal mode, if not matched, increasing s and try again.
            if (!normalMatch(pat, p, str, s)) {
                // End of string, not matched.
                if (s >= str.length()) {
                    return false;
                }

                s++;
                continue;
            }

            return true;
        }
    }

    private char safeCharAt(String value, int index) {
        if (index >= value.length()) {
            return 0;
        }

        return value.charAt(index);
    }
}
