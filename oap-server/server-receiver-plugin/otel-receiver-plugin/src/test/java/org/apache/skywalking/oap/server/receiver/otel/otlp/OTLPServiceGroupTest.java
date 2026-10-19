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
 */

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OTLPServiceGroupTest {
    private static final List<String> KEYS = List.of("service.namespace", "k8s.namespace.name");

    @Test
    void theFirstKeyTheResourceCarriesIsTheGroup() {
        assertEquals("opentelemetry-demo::cart", OTLPServiceGroup.apply("cart",
            Map.of("service.namespace", "opentelemetry-demo", "k8s.namespace.name", "otel-demo"), KEYS));
    }

    @Test
    void aMissingKeyFallsBackToTheNext() {
        assertEquals("finance::billing",
            OTLPServiceGroup.apply("billing", Map.of("k8s.namespace.name", "finance"), KEYS));
    }

    @Test
    void aBlankValueCountsAsMissing() {
        assertEquals("finance::billing", OTLPServiceGroup.apply("billing",
            Map.of("service.namespace", " ", "k8s.namespace.name", "finance"), KEYS));
    }

    @Test
    void noKeyNoGroup() {
        assertEquals("legacy", OTLPServiceGroup.apply("legacy", Map.of("host.name", "vm-1"), KEYS));
        assertEquals("cart", OTLPServiceGroup.apply("cart", Map.of("service.namespace", "shop"), List.of()));
    }

    @Test
    void aNameTheSdkGroupedAlreadyIsKept() {
        assertEquals("shop::cart",
            OTLPServiceGroup.apply("shop::cart", Map.of("service.namespace", "other"), KEYS));
    }

    @Test
    void aValueHoldingTheSeparatorGroupsNothing() {
        // `a::b::cart` would split as the group `a` and the short name `b::cart`.
        assertEquals("cart", OTLPServiceGroup.apply("cart", Map.of("service.namespace", "a::b"), KEYS));
    }
}
