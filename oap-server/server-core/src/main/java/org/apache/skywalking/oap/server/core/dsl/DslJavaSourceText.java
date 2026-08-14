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

package org.apache.skywalking.oap.server.core.dsl;

/**
 * Makes arbitrary rule text safe to embed in the Java source the DSL compilers generate.
 *
 * <p>None of the four compilers emits bytecode directly: each assembles a Java source string and
 * hands it to Javassist. Rule text therefore lands in one of exactly two positions, and each has
 * its own hazard:
 *
 * <ul>
 *   <li><b>identifier</b> — a class or variable name. {@code /}, {@code -} and {@code .} are not
 *       identifier characters, so {@code otel-rules/vm.yaml} cannot be spliced in raw.
 *       See {@link #toIdentifier}.</li>
 *   <li><b>string literal</b> — a metric name, tag key, regex, or the verbatim DSL embedded for
 *       dsl-debugging. A {@code "} or {@code \} in the rule tears the literal open.
 *       See {@link #toLiteral}.</li>
 * </ul>
 *
 * <p>Both were copied per compiler before this class existed — {@code escapeJava} four times,
 * char-identical in three of them.
 */
public final class DslJavaSourceText {

    private DslJavaSourceText() {
    }

    /**
     * Makes a string usable in identifier position.
     *
     * @param name any string, may be null
     * @return the identifier-safe form; {@code "Generated"} for null or empty
     */
    public static String toIdentifier(final String name) {
        if (name == null || name.isEmpty()) {
            return "Generated";
        }
        final StringBuilder sb = new StringBuilder(name.length() + 1);
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            sb.append('_');
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return sb.toString();
    }

    /**
     * Escapes a string for embedding in a Java source-string literal.
     *
     * <p>Only the five characters Javassist's compiler cannot read raw are escaped. {@code
     * commons-text}'s {@code StringEscapeUtils.escapeJava} is NOT a substitute: it also escapes
     * non-ASCII to a {@code u}-prefixed hex escape, and Javassist has no unicode-escape pre-lex
     * phase the way javac does, so a rule containing non-ASCII text — a Chinese tag key, say —
     * would compile with the escape sequence taken literally.
     *
     * @param s the raw string, may be null
     * @return the escaped form; empty for null, so a rule with no text still compiles
     */
    public static String toLiteral(final String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
