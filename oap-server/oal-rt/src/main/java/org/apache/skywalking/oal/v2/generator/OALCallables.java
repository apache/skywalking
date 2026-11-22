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

package org.apache.skywalking.oal.v2.generator;

import org.apache.skywalking.oap.server.core.dsl.DslCallables;

/**
 * What an OAL attribute path may read (see the OAL documentation): the getters of the source class and of its nested attribute
 * types ({@code protocol.http.latency}, {@code tag["key"]}); a JDK value read on the way ({@code name.bytes} on a
 * String) has none. Built the first time a script compiles. OAL calls no methods.
 */
final class OALCallables {

    private OALCallables() {
    }

    static DslCallables get() {
        return Holder.LIST;
    }

    private static final class Holder {
        static final DslCallables LIST = DslCallables.builder("an OAL script", "the OAL documentation")
            // the source's attribute types: their getters; a JDK value (a String, a number, a collection, an
            // Instant) has no attributes for a path to read
            .gettersOfUnlistedTypes(type -> !isJdk(type))
            .build();
    }

    private static boolean isJdk(final Class<?> type) {
        final ClassLoader loader = type.getClassLoader();
        return loader == null || loader == ClassLoader.getPlatformClassLoader();
    }
}
