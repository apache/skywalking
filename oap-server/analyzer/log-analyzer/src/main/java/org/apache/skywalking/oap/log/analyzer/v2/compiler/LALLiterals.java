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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.math.BigInteger;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import org.apache.skywalking.oap.server.core.dsl.DslStrings;

/**
 * Reads a rule's literals when it compiles, as Groovy reads them: a whole number with a leading {@code 0} is octal
 * ({@code 010} is 8), a decimal is a double ({@code 00.5} is 0.5), an {@code L}, {@code F} or {@code D} suffix types a
 * number, and a quoted string is the text between its quotes. A value the rule reads at run time converts in
 * {@code LalRuntimeHelper}.
 */
final class LALLiterals {

    private LALLiterals() {
        // utility class
    }

    // ==================== Numbers ====================

    /**
     * A number literal's type suffix ({@code L}, {@code F}, {@code D}, either case), or 0 when it has none.
     */
    static char suffixOf(final String numText) {
        if (numText.isEmpty()) {
            return 0;
        }
        final char last = numText.charAt(numText.length() - 1);
        return "LlFfDd".indexOf(last) >= 0 ? last : 0;
    }

    static String withoutSuffix(final String numText) {
        return suffixOf(numText) == 0 ? numText : numText.substring(0, numText.length() - 1);
    }

    /**
     * Whether a number literal is written as a decimal: with a point or an exponent.
     */
    static boolean isDecimal(final String numText) {
        return numText.contains(".") || numText.contains("e") || numText.contains("E");
    }

    /**
     * Parsed representation of a NUMBER literal token. Carries the inferred
     * Java type and the literal text trimmed/suffixed for direct emission as
     * Java source.
     */
    static final class NumericLiteral {
        final LALValueCodegen.ExprType type;
        /** Java-source representation, e.g. "10000", "10000L", "1.5", "1.5f". */
        final String javaText;

        private NumericLiteral(final LALValueCodegen.ExprType type, final String javaText) {
            this.type = type;
            this.javaText = javaText;
        }

        static NumericLiteral parse(final String numText) {
            final char suffix = suffixOf(numText);
            String t = withoutSuffix(numText);
            final boolean fractional = isDecimal(t);
            final boolean floating = fractional || suffix == 'F' || suffix == 'f' || suffix == 'D' || suffix == 'd';
            if (floating) {
                // 00.5 is 0.5 (only a whole number is octal), but Javassist cannot read the leading zeros.
                while (t.length() > 1 && t.charAt(0) == '0' && Character.isDigit(t.charAt(1))) {
                    t = t.substring(1);
                }
            }
            switch (suffix) {
                case 'F':
                case 'f':
                    requireFinite(numText, Float.parseFloat(t));
                    return new NumericLiteral(LALValueCodegen.ExprType.FLOAT,
                        (fractional ? t : (t + ".0")) + "f");
                case 'D':
                case 'd':
                    requireFinite(numText, Double.parseDouble(t));
                    return new NumericLiteral(LALValueCodegen.ExprType.DOUBLE,
                        (fractional ? t : (t + ".0")) + "d");
                default:
                    if (fractional) {
                        requireFinite(numText, Double.parseDouble(t));
                        return new NumericLiteral(LALValueCodegen.ExprType.DOUBLE, t);
                    }
                    // Emitted in decimal, so an octal literal is not read again by Java rules downstream.
                    final long value = integerValue(numText, t);
                    if (suffix == 0 && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        return new NumericLiteral(LALValueCodegen.ExprType.INT, Long.toString(value));
                    }
                    return new NumericLiteral(LALValueCodegen.ExprType.LONG, value + "L");
            }
        }

        /**
         * A whole-number literal's value ({@link DslNumbers#wholeLiteral}: one with a leading zero is octal). Groovy
         * reads one beyond a long as a {@code BigInteger}, which Java (and Javassist) would silently wrap.
         */
        private static long integerValue(final String literal, final String digits) {
            final BigInteger value = DslNumbers.wholeLiteral(digits, "The number " + literal);
            if (value.bitLength() > Long.SIZE - 1) {
                throw new IllegalArgumentException(
                    "The number " + literal + " is not a whole number a long holds; Groovy LAL read a larger one as a "
                        + "BigInteger. Compare it as a string, or write it as a decimal for a double");
            }
            return value.longValue();
        }

        /**
         * @return the literal as an object of its own type, {@code Integer.valueOf(5)}
         */
        String boxed() {
            return type.wrapperName + ".valueOf(" + javaText + ")";
        }

        Class<?> boxedClass() {
            return type.valueClass;
        }

        /**
         * The literal written in a numeric type at least as wide as its own, so a comparison stays in that type: an
         * {@code int} literal against a {@code long} is {@code 5L}; a float one against a double is the float's value
         * widened ({@code 0.1f} is not the double {@code 0.1}), as Groovy compares it.
         */
        String as(final LALValueCodegen.ExprType target) {
            if (type.compareTo(target) >= 0) {
                return javaText;
            }
            if (type == LALValueCodegen.ExprType.FLOAT && target == LALValueCodegen.ExprType.DOUBLE) {
                return "((double) " + javaText + ")";
            }
            final String body = withoutSuffix(javaText);
            final boolean fractional = isDecimal(body);
            switch (target) {
                case LONG:
                    return body + "L";
                case FLOAT:
                    return (fractional ? body : body + ".0") + "f";
                case DOUBLE:
                    return fractional ? body : body + ".0";
                default:
                    throw new IllegalStateException("A number literal is not written as " + target);
            }
        }

        private static void requireFinite(final String literal, final double value) {
            if (Double.isInfinite(value)) {
                throw new IllegalArgumentException("The number " + literal + " is too large for a double");
            }
        }
    }

    /**
     * The Java text of a number literal, checked: see {@link NumericLiteral}.
     */
    static String javaText(final String numText) {
        return NumericLiteral.parse(numText).javaText;
    }

    // ==================== Strings ====================

    /**
     * The value of a {@code SLASHY_STRING} token, {@code $/.../$}: the text between its delimiters.
     */
    static String slashyLiteral(final String token) {
        return token.substring(2, token.length() - 2);
    }

    /**
     * The value of a quoted {@code STRING} token: the text between the quotes as written,
     * a regexp's {@code \d} or {@code \s} and a path's {@code C:\temp} alike.
     */
    static String stringLiteral(final String token) {
        return token.substring(1, token.length() - 1);
    }

    /**
     * Appends the escape whose backslash is at {@code i} of an interpolated string's literal text: {@code \$} before
     * what would interpolate ({@code \${x}}, {@code \$x}) is that text; any other escape stays as written.
     *
     * @return the index after the escape
     */
    static int appendEscape(final String s, final int i, final StringBuilder out) {
        final char e = s.charAt(i + 1);
        if (DslStrings.interpolationAt(s, i + 1)) {
            out.append('$');
        } else {
            out.append('\\').append(e);
        }
        return i + 2;
    }
}
