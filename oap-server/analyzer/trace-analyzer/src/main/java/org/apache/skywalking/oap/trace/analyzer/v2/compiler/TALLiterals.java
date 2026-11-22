/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;

/**
 * How a rule's literals read when it compiles, in one place: numbers (octal after a leading zero, decimal, the
 * configuration slots, a condition's number), string escapes and quotes, and the placeholders of a rate-limit id and of
 * a log message. What a value converts to when the rule runs is {@code TalValueConversions}'.
 */
final class TALLiterals {
    private static final Pattern LEADING_ZERO_INTEGER = Pattern.compile("0([0-9]+)([Ll]?)");

    private static final Pattern LEADING_ZEROS = Pattern.compile("^0+(?=[0-9])");

    private static final Pattern ESCAPED_PLACEHOLDER = Pattern.compile("(\\\\+)\\{}");

    private static final Pattern WHOLE_NUMBER = Pattern.compile("([0-9]+)[Ll]?");

    /**
     * @return a STRING token's body with only its own quote character unescaped, every other escape kept as written
     */
    static String quotesUnescaped(final String token) {
        final char quote = token.charAt(0);
        final String body = stripQuotes(token);
        final StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                final char next = body.charAt(++i);
                if (next != quote) {
                    out.append(c);
                }
                out.append(next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A number in an expression, as decimal text every reading of it agrees on: an integer with a leading zero is
     * octal, as Groovy read it, typed by its value as a decimal literal is, and a fraction, an exponent or an
     * {@code F} / {@code D} suffix after a leading zero is decimal, as Java reads it. Javassist would read {@code 08}
     * as 8, refuse {@code 00.5}, and a comparison read {@code 010} as 10.
     */
    static String expressionNumber(final TerminalNode number) {
        final String text = number.getText();
        final Matcher octal = LEADING_ZERO_INTEGER.matcher(text);
        final String where = "the number " + text + " at line " + number.getSymbol().getLine();
        if (!octal.matches()) {
            final String decimal = LEADING_ZEROS.matcher(text).replaceFirst("");
            final Matcher whole = WHOLE_NUMBER.matcher(decimal);
            if (whole.matches() && new BigInteger(whole.group(1)).bitLength() > 63) {
                // A whole number must fit a long, as in LAL, rather than wrap.
                throw new IllegalArgumentException(where + " is beyond a long");
            }
            return decimal;
        }
        final BigInteger value = DslNumbers.wholeLiteral("0" + octal.group(1), where);
        if (value.bitLength() > 63) {
            throw new IllegalArgumentException(where + " is beyond a long");
        }
        return value.bitLength() > 31 || !octal.group(2).isEmpty() ? value + "L" : value.toString();
    }

    /**
     * Parse a NUMBER literal that must be a plain integer, octal after a leading zero as an expression's is. The
     * lexer's NUMBER token also carries the decimal, exponent and suffix forms arithmetic accepts, which the
     * {@code rpm} and {@code [index]} slots cannot.
     */
    private static long parseStrictInteger(final String numText, final String slot) {
        if (!isWholeDigits(numText)) {
            throw new IllegalArgumentException(
                slot + " expects a plain integer literal, got '" + numText
                    + "' (suffixes / decimals / exponents are not accepted here)");
        }
        final BigInteger value = DslNumbers.wholeLiteral(numText, slot + " value '" + numText + "'");
        if (value.bitLength() > 63) {
            throw new IllegalArgumentException(
                slot + " value '" + numText + "' exceeds the supported range (must fit in a Java long)");
        }
        return value.longValue();
    }

    static int parseStrictInt(final String numText, final String slot) {
        final long value = parseStrictInteger(numText, slot);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                slot + " value '" + numText + "' exceeds the supported range (must fit in a Java int)");
        }
        return (int) value;
    }

    /**
     * A STRING token's value, as TAL reads it: the quotes removed and a backslash escaping only a quote or
     * itself, so {@code "a\"b"} is {@code a"b} and {@code "C:\temp"} keeps its backslash; any other escape, such as a
     * regexp's {@code \d} or {@code \s}, is kept as written.
     */
    static String unquote(final String token) {
        final String body = stripQuotes(token);
        return body == null ? null : resolveEscapes(body, false);
    }

    /**
     * @param interpolated the literal text of an interpolated rate-limit id, where {@code \$} before an opening brace
     *                     is the text of a placeholder ({@code \${x}} reads {@code ${x}}); elsewhere it stays as written
     */
    static String resolveEscapes(final String s, final boolean interpolated) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        final StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                i = appendEscape(s, i, out, interpolated);
            } else {
                out.append(s.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /**
     * @return the index after the escape whose backslash is at {@code i}, its value appended
     */
    private static int appendEscape(final String s, final int i, final StringBuilder out,
                                    final boolean interpolated) {
        final char e = s.charAt(i + 1);
        final boolean placeholderText = interpolated && e == '$' && i + 2 < s.length() && s.charAt(i + 2) == '{';
        if (e == '"' || e == '\'' || e == '\\' || placeholderText) {
            out.append(e);
        } else {
            out.append('\\').append(e);
        }
        return i + 2;
    }

    static String stripQuotes(final String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        final char first = s.charAt(0);
        if ((first == '\'' || first == '"') && s.charAt(s.length() - 1) == first) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * @return the index of the next <code>${</code> at or after {@code from} that no backslash escapes, or -1
     */
    static int placeholderAt(final String s, final int from) {
        for (int i = from; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                i++;
            } else if (s.charAt(i) == '$' && s.charAt(i + 1) == '{') {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return the index of the quote that closes the string literal opening at {@code open}, past backslash escapes;
     * -1 when the literal does not close
     */
    static int closingQuote(final String s, final int open) {
        final char quote = s.charAt(open);
        for (int i = open + 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return whether a text is digits alone, the form a configuration slot takes
     */
    static boolean isWholeDigits(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return !text.isEmpty();
    }

    /**
     * A NUMBER literal as {@link #expressionNumber} leaves it, which refuses a whole number beyond a long: its Java
     * type and its text for direct emission as Java source.
     */
    static final class NumericLiteral {
        final ExprType type;
        /** Java-source representation, e.g. "10000", "10000L", "1.5", "1.5f". */
        final String javaText;

        private NumericLiteral(final ExprType type, final String javaText) {
            this.type = type;
            this.javaText = javaText;
        }

        static NumericLiteral parse(final String numText) {
            String t = numText;
            char suffix = 0;
            if (!t.isEmpty()) {
                final char last = t.charAt(t.length() - 1);
                if (last == 'L' || last == 'l' || last == 'F' || last == 'f'
                        || last == 'D' || last == 'd') {
                    suffix = last;
                    t = t.substring(0, t.length() - 1);
                }
            }
            final boolean fractional = t.contains(".") || t.contains("e") || t.contains("E");
            switch (suffix) {
                case 'L':
                case 'l':
                    return new NumericLiteral(ExprType.LONG, t + "L");
                case 'F':
                case 'f':
                    return new NumericLiteral(ExprType.FLOAT,
                        (fractional ? t : (t + ".0")) + "f");
                case 'D':
                case 'd':
                    return new NumericLiteral(ExprType.DOUBLE,
                        (fractional ? t : (t + ".0")) + "d");
                default:
                    if (fractional) {
                        return new NumericLiteral(ExprType.DOUBLE, t);
                    }
                    // An int when it fits, else a long.
                    try {
                        Integer.parseInt(t);
                        return new NumericLiteral(ExprType.INT, t);
                    } catch (NumberFormatException ignoredInt) {
                        return new NumericLiteral(ExprType.LONG, t + "L");
                    }
            }
        }

        /**
         * @return the literal's value as the compiled code holds it: an {@code Integer}, a {@code Long}, a
         *     {@code Float} or a {@code Double}
         */
        Object value() {
            switch (type) {
                case INT:
                    return Integer.valueOf(javaText);
                case LONG:
                    return Long.valueOf(javaText.substring(0, javaText.length() - 1));
                case FLOAT:
                    return Float.valueOf(javaText);
                default:
                    return Double.valueOf(javaText);
            }
        }
    }

    /**
     * The {@code {}} placeholders the logger fills in a message: a {@code {}} after an odd run of backslashes is
     * literal text, one after an even run a placeholder.
     */
    static int placeholderCount(final String message) {
        int count = 0;
        for (int i = message.indexOf("{}"); i >= 0; i = message.indexOf("{}", i + 2)) {
            int backslashes = 0;
            while (i - backslashes > 0 && message.charAt(i - backslashes - 1) == '\\') {
                backslashes++;
            }
            if (backslashes % 2 == 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * The message as the formatter reads its escapes: each run of backslashes before {@code {}} halved, so an odd
     * run leaves a literal {@code {}} and an even one an unfilled placeholder, which prints as it is.
     */
    static String unescapedPlaceholders(final String message) {
        final Matcher run = ESCAPED_PLACEHOLDER.matcher(message);
        final StringBuilder out = new StringBuilder();
        while (run.find()) {
            run.appendReplacement(out, Matcher.quoteReplacement("\\".repeat(run.group(1).length() / 2) + "{}"));
        }
        run.appendTail(out);
        return out.toString();
    }

    private TALLiterals() {
    }
}
