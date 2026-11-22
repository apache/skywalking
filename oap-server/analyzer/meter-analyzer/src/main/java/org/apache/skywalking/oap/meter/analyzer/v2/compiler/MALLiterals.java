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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.math.BigDecimal;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

/**
 * How MAL reads a literal at compile time: numbers (a leading 0 is octal, as in Groovy) and quoted strings (Groovy's
 * escapes). The values the generated code converts at run time are read by
 * {@link org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper}.
 */
final class MALLiterals {

    private MALLiterals() {
    }

    /**
     * A literal's double.
     */
    static double parseLiteral(final String text) {
        return Double.parseDouble(decimalText(text));
    }

    /**
     * @return the literal's text in decimal: an integer with a leading 0 is octal, as in Groovy and Java (010 is 8);
     *     08 or 09 is refused, as Groovy refuses it
     */
    static String decimalText(final String text) {
        return text.indexOf('.') < 0 ? DslNumbers.wholeLiteral(text, "The literal " + text).toString() : text;
    }

    /**
     * @return the literal's exact value (a leading 0 octal)
     */
    static BigDecimal exactValue(final String text) {
        return new BigDecimal(decimalText(text));
    }

    /**
     * A list entry, taken as a double: refused when the double is another number than the text (99.00000000000000001).
     */
    static double parseListNumber(final String text) {
        final double value = parseLiteral(text);
        if (exactValue(text).compareTo(BigDecimal.valueOf(value)) != 0) {
            throw new IllegalArgumentException("The list entry " + text + " would be read as " + value);
        }
        return value;
    }

    /**
     * A closure literal, which the generated Java computes with: refused when the double cannot hold the integer
     * exactly (Groovy keeps it as a Long: {@code 9007199254740993} would be {@code 9007199254740992}).
     */
    static double parseNumber(final String text) {
        final double value = parseLiteral(text);
        if (text.indexOf('.') < 0 && exactValue(text).compareTo(new BigDecimal(value)) != 0) {
            throw new IllegalArgumentException(
                "Integer literal " + text + " is beyond what the compiled double holds exactly");
        }
        return value;
    }

    /**
     * A quoted literal where this parser does not interpolate (arguments, string lists, map keys, {@code in} lists):
     * Groovy would interpolate a double-quoted {@code ${...}} or {@code $name} there, so refuse it; any other {@code $}
     * is the character.
     */
    static String literalText(final String token) {
        if (token.startsWith("\"") && DslStrings.firstInterpolation(token.substring(1, token.length() - 1)) >= 0) {
            throw new IllegalArgumentException(
                "Interpolation is not supported in " + token + "; use a single-quoted string");
        }
        return stripQuotes(token);
    }

    static String stripQuotes(final String s) {
        if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"')) {
            return unescapeString(s.substring(1, s.length() - 1));
        }
        return s;
    }

    /**
     * A string's escapes, as MAL has always read them: {@code \\}, {@code \n}, {@code \r}, {@code \t} and the quotes
     * are the characters; any other escape ({@code \b} or {@code \$} in a regex, {@code \s}, an octal one) stays as
     * written, so a regex receives it.
     */
    static String unescapeString(final String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                final char next = s.charAt(i + 1);
                switch (next) {
                    case '\\':
                        sb.append('\\');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    default:
                        sb.append(c).append(next);
                        break;
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
