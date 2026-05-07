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

package org.apache.skywalking.oap.server.core.dsldebug;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.classloader.Catalog;

/**
 * Typed identity for one rule in the DSL debug API. Replaces every
 * "{catalog}/{name}/{ruleName}" string-encoded identifier the debug session
 * code might otherwise pass around — three typed fields, generated
 * equals/hashCode/toString from Lombok. Lives in {@code server-core} so every
 * DSL module can reference it without dragging in the analyzer modules.
 *
 * <p>{@link Catalog} is the existing wire-name-mapped enum already used by
 * the runtime-rule REST handler; reusing it here keeps the same set of
 * acceptable values across the whole admin surface. Phase 1 (MAL) covers the
 * {@code OTEL_RULES}, {@code LOG_MAL_RULES}, and {@code TELEGRAF_RULES}
 * catalogs; phase 2 adds {@code LAL}; phase 3 adds an {@code OAL} value
 * when OAL probes land.
 *
 * <p>{@code ruleName} disambiguates when a single rule file declares
 * multiple metrics — e.g. an OAL file with several metric definitions, or an
 * MAL bundle that emits more than one metric name. A debug session always
 * targets one specific rule; the holder lookup walks this triple.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class RuleKey {

    private final Catalog catalog;
    private final String name;
    private final String ruleName;

    public RuleKey(final Catalog catalog, final String name, final String ruleName) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.name = Objects.requireNonNull(name, "name");
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName");
    }
}
