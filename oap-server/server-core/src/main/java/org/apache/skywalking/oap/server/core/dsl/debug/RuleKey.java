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

package org.apache.skywalking.oap.server.core.dsl.debug;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.dsl.Catalog;

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
 * {@code OTEL_RULES}, {@code LOG_MAL_RULES}, {@code TELEGRAF_RULES}, and
 * {@code METER_ANALYZER_CONFIG} catalogs; phase 2 adds {@code LAL}; phase 3
 * adds an {@code OAL} value when OAL probes land.
 *
 * <p>{@code ruleName} disambiguates when a single rule file declares
 * multiple metrics — e.g. an OAL file with several metric definitions, or an
 * MAL bundle that emits more than one metric name. A debug session always
 * targets one specific rule; the holder lookup walks this triple.
 *
 * <p><b>{@code name} is canonicalised here, in the constructor, on purpose.</b> It identifies a
 * rule FILE, and the routes that produce it disagreed on whether to spell the extension: LAL's
 * boot loader published {@code default.yaml} while its runtime-rule engine published
 * {@code default}, so a hot update registered a second binding beside the static one instead of
 * replacing it. Nothing failed — the two keys simply never met — and an operator addressing the
 * older spelling then toggled a {@link GateHolder} belonging to a compiled rule that no longer
 * evaluates anything, which is indistinguishable from a rule with no traffic. Normalising at each
 * publish site would have left the next site free to get it wrong; doing it here also lets the
 * REST API keep accepting both spellings, so no existing operator script breaks.
 *
 * <p>Only a YAML extension is stripped. OAL rule files are {@code core.oal}, MAL bundles nest as
 * {@code activemq/activemq-broker}, and a file legitimately named {@code vm.linux.yaml} keeps its
 * {@code vm.linux}.
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
        this.name = canonicalName(Objects.requireNonNull(name, "name"));
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName");
    }

    /**
     * Drops a trailing YAML extension so the same rule file is one key however it was spelled.
     *
     * @param name the rule file name, as the caller knows it
     * @return the canonical form
     */
    private static String canonicalName(final String name) {
        if (name.endsWith(".yaml")) {
            return name.substring(0, name.length() - ".yaml".length());
        }
        if (name.endsWith(".yml")) {
            return name.substring(0, name.length() - ".yml".length());
        }
        return name;
    }
}
