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
 *
 */

package org.apache.skywalking.oal.v2.util;

/**
 * OAL's value conversions, in one place: the casts of from(), and the literals of a script as the generated code reads
 * them. OAL string literals take Java's escapes. Javassist, which compiles the generated source, reads only {@code \n},
 * {@code \t}, {@code \r} and {@code \f} and takes any other escaped character as itself, so a literal is decoded once
 * when parsed and written back with no other escape; any other character, a control one too, is written as itself.
 */
public class TypeCastUtil {
    /**
     * @param castType           to change the value of given original expression.
     * @param originalExpression to read the value
     * @return cast expression if cast type exists and is legal.
     */
    public static String withCast(String castType, String originalExpression) {
        if (castType == null) {
            return originalExpression;
        }
        switch (castType) {
            case "(str->long)":
            case "(long)":
                return "Long.parseLong(" + originalExpression + ")";
            case "(str->int)":
            case "(int)":
                return "Integer.parseInt(" + originalExpression + ")";
            default:
                throw new IllegalArgumentException(
                    "castType:" + castType + " is legal, context expression:" + originalExpression);
        }
    }

    /**
     * A value read from the source, as the parameter it passes to takes it: cast to an int, long or double parameter,
     * and a boolean as 1 or 0, as Javassist's cast of one gives.
     *
     * @param valueType the type the expression reads, or null when it is not plain to see.
     */
    public static String toParameter(Class<?> valueType, Class<?> parameterType, String expression) {
        if (valueType == boolean.class
            && (parameterType == int.class || parameterType == long.class || parameterType == double.class)) {
            return "(" + expression + " ? (" + parameterType.getName() + ") 1 : (" + parameterType.getName() + ") 0)";
        }
        if (parameterType.equals(long.class)) {
            return "(long)(" + expression + ")";
        } else if (parameterType.equals(int.class)) {
            return "(int)(" + expression + ")";
        } else if (parameterType.equals(double.class)) {
            return "(double)(" + expression + ")";
        }
        return expression;
    }

    /**
     * Literals parsed from a script are {@code Long}; one built through the model API may be another integral type.
     */
    public static Object integralAsLong(Object literal) {
        return literal instanceof Integer || literal instanceof Short || literal instanceof Byte
            ? (Object) ((Number) literal).longValue() : literal;
    }

    /**
     * @param quoted the literal as written, quotes included, which the lexer has checked to be well formed.
     * @return the text between its quotes, as written: no escape decoded.
     */
    public static String unquoted(String quoted) {
        return quoted.substring(1, quoted.length() - 1);
    }

    /**
     * @param quoted the literal as written, quotes included, which the lexer has checked to be well formed.
     * @return its value.
     */
    public static String decode(String quoted) {
        final String body = unquoted(quoted);
        final StringBuilder value = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') {
                c = body.charAt(++i);
                switch (c) {
                    case 'b':
                        c = '\b';
                        break;
                    case 't':
                        c = '\t';
                        break;
                    case 'n':
                        c = '\n';
                        break;
                    case 'f':
                        c = '\f';
                        break;
                    case 'r':
                        c = '\r';
                        break;
                    case 'u':
                        while (body.charAt(i + 1) == 'u') {
                            i++;
                        }
                        c = (char) Integer.parseInt(body.substring(i + 1, i + 5), 16);
                        i += 4;
                        break;
                    default:
                        if (c >= '0' && c <= '7') {
                            int end = i + 1;
                            final int max = c <= '3' ? i + 3 : i + 2;
                            while (end < max && end < body.length() && body.charAt(end) >= '0' && body.charAt(end) <= '7') {
                                end++;
                            }
                            c = (char) Integer.parseInt(body.substring(i, end), 8);
                            i = end - 1;
                        }
                        // \" \' and \\ stand for themselves.
                }
            }
            value.append(c);
        }
        return value.toString();
    }

    /**
     * @return the value as a string literal of the source Javassist compiles.
     */
    public static String toJavaSource(String value) {
        final StringBuilder source = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    source.append("\\\"");
                    break;
                case '\\':
                    source.append("\\\\");
                    break;
                case '\t':
                    source.append("\\t");
                    break;
                case '\n':
                    source.append("\\n");
                    break;
                case '\r':
                    source.append("\\r");
                    break;
                case '\f':
                    source.append("\\f");
                    break;
                default:
                    source.append(c);
            }
        }
        return source.append('"').toString();
    }

    /**
     * A number of a filter, as the stock matchers take it: the lexer accepts Java's {@code _} separators and {@code L}
     * suffix, and neither makes the number a string. It is decimal; one with a leading 0 and either, which Java would
     * read as octal, fails.
     *
     * @throws IllegalArgumentException for a number that is not one, with the reason.
     */
    public static long numberValue(String text) {
        String digits = text.replace("_", "");
        if (isLongSuffixed(digits)) {
            digits = digits.substring(0, digits.length() - 1);
        }
        if (digits.length() > 1 && digits.startsWith("0") && !digits.equals(text)) {
            throw new IllegalArgumentException(
                "number " + text + ": a leading 0 would make it octal; write it without");
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("number " + text + " is out of the long range");
        }
    }

    /**
     * A number of a filter as written, which a matcher registered under an operator's name receives: its value where
     * Java reads the text as a long, else, written with an {@code L} or a {@code _} or out of the long range, the text.
     *
     * @return a {@code Long}, or the text as a {@code String}
     */
    public static Object numberAsWritten(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return text;
        }
    }

    /**
     * A number of a function argument written without an {@code L} ({@link #isLongSuffixed}), as the generated call
     * takes it: its decimal value.
     *
     * @throws IllegalArgumentException for a number that is not one, with the reason.
     */
    public static long argumentNumber(String text) {
        return numberValue(argumentSpelling(text));
    }

    /**
     * A number of a function argument written with an {@code L} ({@link #isLongSuffixed}), as the generated call takes
     * it: as written, for the overload Javassist picks, with no check of its value or range.
     *
     * @throws IllegalArgumentException for a number with a {@code _}, which Javassist does not read.
     */
    public static String argumentSpelling(String text) {
        if (text.indexOf('_') >= 0) {
            throw new IllegalArgumentException("number " + text + ": a function argument is written without _");
        }
        return text;
    }

    public static boolean isLongSuffixed(String number) {
        return Character.toUpperCase(number.charAt(number.length() - 1)) == 'L';
    }
}
