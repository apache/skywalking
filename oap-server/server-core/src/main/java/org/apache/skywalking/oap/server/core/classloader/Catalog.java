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

package org.apache.skywalking.oap.server.core.classloader;

import lombok.Getter;

/**
 * Strongly-typed catalog identifier consumed by {@link DSLClassLoaderManager} so the manager's
 * keys are comparable without string-typo risk. Each entry's {@link #wireName} matches the
 * catalog string used everywhere else in the runtime-rule pipeline (REST surface, DAO row,
 * loader-name prefix, {@code StaticRuleRegistry} key) so conversion at the boundary via
 * {@link #of(String)} keeps the rest of the codebase on plain {@code String catalog}.
 */
public enum Catalog {
    OTEL_RULES("otel-rules"),
    LOG_MAL_RULES("log-mal-rules"),
    TELEGRAF_RULES("telegraf-rules"),
    LAL("lal");

    @Getter
    private final String wireName;

    Catalog(final String wireName) {
        this.wireName = wireName;
    }

    /**
     * Resolve {@code wireName} to the matching enum. Throws {@link IllegalArgumentException} on
     * an unknown catalog so callers fail fast rather than silently dropping the rule.
     */
    public static Catalog of(final String wireName) {
        for (final Catalog c : values()) {
            if (c.wireName.equals(wireName)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown DSL catalog: " + wireName);
    }
}
