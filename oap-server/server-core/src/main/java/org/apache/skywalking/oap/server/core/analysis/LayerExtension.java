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

/**
 * SPI for out-of-tree extensions to register custom {@link Layer} values without
 * modifying the OAP source. Implementations are discovered by {@code LayerExtensionLoader}
 * via {@code ServiceLoader} during {@code CoreModuleProvider.prepare()}, before any
 * downstream module wires up.
 *
 * <p>Implementations call {@link Layer#register(String, int, boolean)} for each
 * layer they want to contribute. Name-shape, name-uniqueness, ordinal-uniqueness, and
 * seal-state checks are enforced by that method, so an implementation cannot bypass them
 * by going through this SPI.
 *
 * <p>Register on the classpath via
 * {@code META-INF/services/org.apache.skywalking.oap.server.core.analysis.LayerExtension}.
 *
 * <p>Example:
 * <pre>
 * public final class IotFleetLayers implements LayerExtension {
 *     &#64;Override
 *     public void contribute() {
 *         Layer.register("IOT_FLEET",   1000, true);
 *         Layer.register("IOT_GATEWAY", 1001, true);
 *     }
 * }
 * </pre>
 *
 * <p>SPI is preferred over registering from a downstream module's {@code prepare()} because
 * Core's {@code prepare()} runs before every other module's {@code prepare()} — there is no
 * way for a downstream module to register layers earlier than Core, and the registry is
 * sealed at the start of {@code Core.notifyAfterCompleted()}.
 */
public interface LayerExtension {
    /**
     * Called once during Core boot. Implementations should call
     * {@link Layer#register(String, int, boolean)} one or more times.
     */
    void contribute();
}
