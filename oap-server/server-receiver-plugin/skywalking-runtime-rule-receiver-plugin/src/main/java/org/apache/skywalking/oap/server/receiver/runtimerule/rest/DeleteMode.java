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

package org.apache.skywalking.oap.server.receiver.runtimerule.rest;

import lombok.Getter;

/**
 * The {@code ?mode=} parameter on {@code POST /runtime/rule/delete}. Parsed from the wire
 * string at the REST boundary so the rest of the codebase deals with a typed value instead
 * of free-form strings.
 */
public enum DeleteMode {
    /** No mode flag — apply the default {@code /delete} behaviour. With no bundled twin
     *  on disk, the row is dropped and the backend measure (if any) is left in place as
     *  an inert artefact (operator-side cleanup of orphaned schemas is out of scope, same
     *  as for static rules removed from {@code otel-rules/}). With a bundled twin, the
     *  request is refused with {@code 409 requires_revert_to_bundled} so letting bundled
     *  silently take over the {@code (catalog, name)} requires an explicit operator
     *  decision. */
    DEFAULT(""),
    /** Operator explicitly asked to revert this rule to its bundled YAML. Runs the
     *  schema-change pipeline (install runtime locally, apply bundled through the
     *  standard pipeline so the runtime→bundled delta drops runtime-only metrics and
     *  installs bundled-only ones) before removing the row. Returns {@code 400
     *  no_bundled_twin} when no bundled YAML exists on disk for {@code (catalog, name)}. */
    REVERT_TO_BUNDLED("revertToBundled");

    @Getter
    private final String wireValue;

    DeleteMode(final String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Parse the wire value (query-string form) to its enum. {@code null} or empty returns
     * {@link #DEFAULT}; {@code "revertToBundled"} (case-insensitive) returns
     * {@link #REVERT_TO_BUNDLED}; anything else throws {@link IllegalArgumentException}.
     */
    public static DeleteMode of(final String wireValue) {
        if (wireValue == null || wireValue.isEmpty()) {
            return DEFAULT;
        }
        for (final DeleteMode m : values()) {
            if (m.wireValue.equalsIgnoreCase(wireValue)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown delete mode: " + wireValue);
    }
}
