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

package org.apache.skywalking.oap.server.receiver.runtimerule.layer;

import lombok.Getter;

/**
 * Typed exception raised by {@link RuntimeLayerConflictChecker} when a runtime DSL
 * {@code layerDefinitions:} entry cannot be accepted. Carries a stable {@code applyStatus}
 * code so the runtime-rule REST handler can map directly into the existing
 * {@code {applyStatus, catalog, name, message}} response envelope without case analysis.
 *
 * <p>The {@code message} surfaces the offending value verbatim (layer name, conflicting
 * ordinal, declaring source) so the operator's next action is obvious from the response
 * body alone.
 */
@Getter
public final class LayerConflictException extends RuntimeException {

    /**
     * Stable identifier mirrored as the {@code applyStatus} field on the HTTP response.
     * Operators and automation should pattern-match on the enum constant name (which is
     * exactly the wire value via {@link #wireValue()}).
     */
    public enum Status {
        LAYER_ORDINAL_OUT_OF_RANGE("layer_ordinal_out_of_range"),
        LAYER_NAME_CONFLICT("layer_name_conflict"),
        LAYER_ORDINAL_COLLISION("layer_ordinal_collision"),
        LAYER_NAME_INVALID("layer_name_invalid"),
        /** Runtime override of a bundled rule attempted to declare layerDefinitions.
         *  Layer ownership lives with whichever channel registered it first; runtime
         *  overrides may change the rule body but must not touch layer declarations. */
        LAYER_OVERRIDE_FORBIDDEN("layer_override_forbidden");

        private final String wire;

        Status(final String wire) {
            this.wire = wire;
        }

        public String wireValue() {
            return wire;
        }
    }

    private final Status status;

    public LayerConflictException(final Status status, final String message) {
        super(message);
        this.status = status;
    }

    /** Convenience accessor for callers building the HTTP response envelope. */
    public String applyStatus() {
        return status.wireValue();
    }
}
