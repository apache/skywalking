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
 * Test-only {@link LayerExtension} contributed via {@code META-INF/services/...} on the
 * test classpath. Owns ordinals {@code 1160..1169}; consumed by
 * {@link LayerExtensionLoaderTest}. Idempotent — re-running tests in the same JVM is safe
 * because {@link Layer#register} is a no-op on identical re-registration.
 */
public final class TestSpiLayerExtension implements LayerExtension {
    @Override
    public void contribute() {
        Layer.register("TEST_SPI_LAYER_A", 1160, true);
        Layer.register("TEST_SPI_LAYER_B", 1161, false);
    }
}
