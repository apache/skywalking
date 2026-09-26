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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.util.Comparator;

/**
 * The order Go sorts strings in, <code>strings.Compare</code> and the <code>&lt;</code> operator: by their UTF-8
 * bytes, which is the order of their code points. Java's own order of strings is by UTF-16 unit, and puts a character
 * past U+FFFF before U+E000. Every list the Sessionizer sorts by a string, such as ids and map keys, is sorted here
 * with this order, so the two list them alike.
 */
public final class GoStrings {
    /** Go's order of strings, as a comparator. */
    public static final Comparator<String> ORDER = GoStrings::compare;

    private GoStrings() {
    }

    /**
     * @return negative, zero or positive as <code>a</code> sorts before, with or after <code>b</code>
     */
    public static int compare(final String a, final String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            final int ca = a.codePointAt(i);
            final int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Boolean.compare(i < a.length(), j < b.length());
    }
}
