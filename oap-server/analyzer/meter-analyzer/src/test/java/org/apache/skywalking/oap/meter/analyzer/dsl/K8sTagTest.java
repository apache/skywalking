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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.Retag;
import org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.reflect.Whitebox;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class K8sTagTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public ImmutableMap<String, SampleFamily> input;

    @Parameterized.Parameter(2)
    public String expression;

    @Parameterized.Parameter(3)
    public Result want;

    @Parameterized.Parameter(4)
    public boolean isThrow;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "Pod2Service",
                of("container_cpu_usage_seconds_total", SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-mbczh"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod' , 'namespace')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-mbczh",
                                  "service", "nginx-service.default"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx",
                                  "service", "kube-state-metrics.kube-system"
                              ))
                          .value(1)
                          .build()
                ).build()),
                false,
                },
            {
                "Pod2Service_no_pod",
                of("container_cpu_usage_seconds_total", SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-no-pod"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod' , 'namespace')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-no-pod" , "service", Retag.BLANK
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx",
                                  "service", "kube-state-metrics.kube-system"
                              ))
                          .value(1)
                          .build()
                ).build()),
                false,
                },
            {
                "Pod2Service_no_service",
                of("container_cpu_usage_seconds_total", SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-no-service"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod' , 'namespace')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "default", "container", "my-nginx", "cpu", "total", "pod",
                                  "my-nginx-5dc4865748-no-service" , "service", Retag.BLANK
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "namespace", "kube-system", "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx",
                                  "service", "kube-state-metrics.kube-system"
                              ))
                          .value(1)
                          .build()
                ).build()),
                false,
                },
            });
    }

    @SneakyThrows
    @Before
    public void setup() {
        Whitebox.setInternalState(K8sInfoRegistry.class, "INSTANCE",
                                  Mockito.spy(K8sInfoRegistry.getInstance())
        );

        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addService", mockService("nginx-service", "default", of("run", "nginx")))
                    .thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addService",
            mockService("kube-state-metrics", "kube-system", of("run", "kube-state-metrics"))
        ).thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addPod",
            mockPod("my-nginx-5dc4865748-mbczh", "default", of("run", "nginx"))
        ).thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addPod",
            mockPod("kube-state-metrics-6f979fd498-z7xwx", "kube-system", of("run", "kube-state-metrics"))
        ).thenCallRealMethod();

        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "removeService", mockService("nginx-service", "default", of("run", "nginx")))
                    .thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "removePod",
            mockPod("my-nginx-5dc4865748-mbczh", "default", of("run", "nginx"))
        ).thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addService", mockService("nginx-service", "default", of("run", "nginx")))
                    .thenCallRealMethod();
        PowerMockito.when(
            K8sInfoRegistry.getInstance(), "addPod",
            mockPod("my-nginx-5dc4865748-mbczh", "default", of("run", "nginx"))
        ).thenCallRealMethod();

    }

    @Test
    public void test() {
        Expression e = DSL.parse(expression);
        Result r = null;
        try {
            r = e.run(input);
        } catch (Throwable t) {
            if (isThrow) {
                return;
            }
            log.error("Test failed", t);
            fail("Should not throw anything");
        }
        if (isThrow) {
            fail("Should throw something");
        }
        assertThat(r, is(want));
    }

    private V1Service mockService(String name, String namespace, Map<String, String> selector) {
        V1Service service = new V1Service();
        V1ObjectMeta serviceMeta = new V1ObjectMeta();
        V1ServiceSpec v1ServiceSpec = new V1ServiceSpec();

        serviceMeta.setName(name);
        serviceMeta.setNamespace(namespace);
        service.setMetadata(serviceMeta);
        v1ServiceSpec.setSelector(selector);
        service.setSpec(v1ServiceSpec);

        return service;
    }

    private V1Pod mockPod(String name, String namespace, Map<String, String> labels) {
        V1Pod v1Pod = new V1Pod();
        V1ObjectMeta podMeta = new V1ObjectMeta();
        podMeta.setName(name);
        podMeta.setNamespace(namespace);
        podMeta.setLabels(labels);
        v1Pod.setMetadata(podMeta);

        return v1Pod;
    }
}