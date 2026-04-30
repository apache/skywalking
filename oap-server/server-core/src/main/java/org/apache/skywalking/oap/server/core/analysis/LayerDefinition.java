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

package org.apache.skywalking.oap.server.core.analysis;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire format for a single external {@link Layer} declaration. Used by every yaml-driven
 * registration path: the operator-managed {@code layer-extensions.yml}, MAL rule files'
 * {@code layerDefinitions:} block, and LAL rule files' {@code layerDefinitions:} block. All
 * paths funnel through {@link Layer#register(String, int, boolean)}, which enforces
 * name-shape, name-uniqueness, ordinal-uniqueness, and seal-state checks.
 *
 * <p>Defaults to {@code normal=true}.
 */
@Data
@NoArgsConstructor
public class LayerDefinition {
    private String name;
    private int ordinal;
    private boolean normal = true;

    /** Funnels this declaration through the registry; idempotent on identical re-registration. */
    public Layer register() {
        return Layer.register(name, ordinal, normal);
    }
}
