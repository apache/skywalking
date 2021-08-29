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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.google.common.collect.ImmutableSortedMap.of;
import static junit.framework.TestCase.assertEquals;

@RequiredArgsConstructor
@RunWith(Parameterized.class)
public class ServiceNameFormatterTest {
    private final Case kase;

    @Parameterized.Parameters
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

    @Test
    public void testFormatDefaultRule() throws Exception {
        assertEquals(new ServiceNameFormatter(kase.format).format(kase.context), kase.result);
    }

    static V1Service service(final String name) {
        return new V1Service() {
            @Override
            public V1ObjectMeta getMetadata() {
                return new V1ObjectMeta() {
                    @Override
                    public String getName() {
                        return name;
                    }
                };
            }
        };
    }

    static V1Pod pod(ImmutableMap<String, String> lb) {
        return new V1Pod() {
            @Override
            public V1ObjectMeta getMetadata() {
                return new V1ObjectMeta() {
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
