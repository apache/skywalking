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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;

import static com.google.common.collect.ImmutableSortedMap.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceNameFormatterTest {
    public static Case[] parameters() {
        return new Case[] {
            new Case(
                null,
                ImmutableMap.of("pod", pod(of("service.istio.io/canonical-name", "Clash"))),
                "Clash"
            ),
            new Case(
                null,
                ImmutableMap.of("pod", pod(of("service.istio.io/canonical-name", "ClashX", "service.istio.io/canonical-revision", "v1"))),
                "ClashX"
            ),
            new Case(
                "${pod.metadata.labels.(service.istio.io/canonical-name)}-${pod.metadata.labels.(service.istio.io/canonical-revision)}",
                ImmutableMap.of("pod", pod(of("service.istio.io/canonical-name", "Clash", "service.istio.io/canonical-revision", "v1beta"))),
                "Clash-v1beta"
            ),
            new Case(
                "${pod.metadata.labels.(service.istio.io/canonical-name)}",
                ImmutableMap.of("service", service("Clash"), "pod", pod(of("service.istio.io/canonical-name", "ClashX-alpha"))),
                "ClashX-alpha"
            ),
            new Case(
                "${pod.metadata.labels.NOT_EXISTS}",
                ImmutableMap.of("service", service("Clash"), "pod", pod(of("service.istio.io/canonical-name", "ClashX-alpha"))),
                "-"
            ),
            new Case(
                "${pod.metadata.labels.NOT_EXISTS,pod.metadata.labels.(service.istio.io/canonical-name),pod.metadata.labels.app}",
                ImmutableMap.of("service", service("Clash"), "pod", pod(of("app", "ClashX-alpha"))),
                "ClashX-alpha"
            )
        };
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testFormatDefaultRule(final Case kase) throws Exception {
        assertEquals(new ServiceNameFormatter(kase.format).format(kase.context), kase.result);
    }

    static Service service(final String name) {
        return new Service() {
            @Override
            public ObjectMeta getMetadata() {
                return new ObjectMeta() {
                    @Override
                    public String getName() {
                        return name;
                    }
                };
            }
        };
    }

    static Pod pod(ImmutableMap<String, String> lb) {
        return new Pod() {
            @Override
            public ObjectMeta getMetadata() {
                return new ObjectMeta() {
                    @Override
                    public Map<String, String> getLabels() {
                        return lb;
                    }
                };
            }
        };
    }

    @RequiredArgsConstructor
    static class Case {
        private final String format;

        private final Map<String, Object> context;

        private final String result;
    }
}
