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

package org.apache.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Match classes by any one of the given {@link #prefixes}
 */
@SuppressWarnings("rawtypes")
public class PrefixMatch implements IndirectMatch {
    private String[] prefixes;

    private PrefixMatch(String... prefixes) {
        if (prefixes == null || prefixes.length == 0) {
            throw new IllegalArgumentException("prefixes argument is null or empty");
        }
        this.prefixes = prefixes;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction junction = null;

        for (String prefix : prefixes) {
            if (junction == null) {
                junction = ElementMatchers.nameStartsWith(prefix);
            } else {
                junction = junction.or(ElementMatchers.nameStartsWith(prefix));
            }
        }

        return junction;
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        for (final String prefix : prefixes) {
            if (typeDescription.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static PrefixMatch nameStartsWith(final String... prefixes) {
        return new PrefixMatch(prefixes);
    }
}
