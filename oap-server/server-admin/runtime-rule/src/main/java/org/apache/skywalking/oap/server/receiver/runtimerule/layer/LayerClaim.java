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

import java.util.Objects;
import lombok.Getter;

/**
 * Immutable resolved layer declaration used inside the runtime-rule module. Produced from
 * a MAL/LAL {@code layerDefinitions:} entry after the applier has resolved
 * server-allocated ordinals (for entries with no explicit {@code ordinal:}) and validated
 * the {@code >= RUNTIME_DYNAMIC_MIN_ORDINAL} floor.
 *
 * <p>Equality is by the full {@code (name, ordinal, normal)} triple — two claims are
 * "same" iff all three fields match. This is the same notion of identity that
 * {@link org.apache.skywalking.oap.server.core.analysis.Layer#registerDynamic} uses to
 * decide whether re-registration is idempotent.
 */
@Getter
public final class LayerClaim {

    private final String name;
    private final int ordinal;
    private final boolean normal;

    public LayerClaim(final String name, final int ordinal, final boolean normal) {
        if (name == null) {
            throw new IllegalArgumentException("layer name must not be null");
        }
        this.name = name;
        this.ordinal = ordinal;
        this.normal = normal;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LayerClaim)) {
            return false;
        }
        final LayerClaim that = (LayerClaim) o;
        return ordinal == that.ordinal
            && normal == that.normal
            && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ordinal, normal);
    }

    @Override
    public String toString() {
        return "LayerClaim{name=" + name + ", ordinal=" + ordinal + ", normal=" + normal + "}";
    }
}
