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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

/**
 * Per-session capture granularity. Applies to DSLs whose probe surface
 * exposes both a coarse-grained (per stage) and fine-grained (per
 * statement) view — currently LAL. MAL/OAL recorders ignore this knob;
 * their probe shape is single-granularity by design.
 *
 * <ul>
 *   <li>{@link #BLOCK} — only the stage probes fire (text / parser /
 *       extractor / outputRecord for LAL). Default.</li>
 *   <li>{@link #STATEMENT} — additionally each DSL statement records a
 *       {@code line} entry with its source line number and verbatim
 *       text. Higher capture volume; intended for short interactive
 *       debugging sessions.</li>
 * </ul>
 */
public enum Granularity {
    BLOCK,
    STATEMENT;

    public static final Granularity DEFAULT = BLOCK;

    /**
     * Parse a wire-form granularity string. Empty / null / unknown values
     * fall back to {@link #DEFAULT} so a missing param never breaks a
     * legitimate install — the operator sees the chosen mode in the
     * install response and can correct it without a 400.
     */
    public static Granularity ofWireName(final String wire) {
        if (wire == null) {
            return DEFAULT;
        }
        final String trimmed = wire.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT;
        }
        if ("statement".equalsIgnoreCase(trimmed)) {
            return STATEMENT;
        }
        if ("block".equalsIgnoreCase(trimmed)) {
            return BLOCK;
        }
        return DEFAULT;
    }

    /** Lower-cased name used on the REST + cluster wire ({@code "block"} / {@code "statement"}). */
    public String wireName() {
        return name().toLowerCase();
    }
}
