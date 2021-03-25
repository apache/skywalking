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
import java.util.Arrays;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

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
                              of("container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-mbczh"))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-mbczh",
                                  "service", "nginx-service.default"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
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
                              of("container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-no-pod"))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-no-pod"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
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
                              of("container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-no-service"))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
                                  "kube-state-metrics-6f979fd498-z7xwx"
                              ))
                          .value(1)
                          .build()
                ).build()),
                "container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod')",
                Result.success(SampleFamilyBuilder.newBuilder(
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "my-nginx", "cpu", "total", "pod", "my-nginx-5dc4865748-no-service"
                              ))
                          .value(2)
                          .build(),
                    Sample.builder()
                          .labels(
                              of(
                                  "container", "kube-state-metrics", "cpu", "total", "pod",
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

    @Before
    public void setup() {
        Whitebox.setInternalState(K8sInfoRegistry.class, "INSTANCE",
                                  Mockito.spy(K8sInfoRegistry.getInstance())
        );
        when(K8sInfoRegistry.getInstance().findServiceName("my-nginx-5dc4865748-mbczh")).thenReturn(
            "nginx-service.default");
        when(K8sInfoRegistry.getInstance().findServiceName("kube-state-metrics-6f979fd498-z7xwx")).thenReturn(
            "kube-state-metrics.kube-system");
        when(K8sInfoRegistry.getInstance().findServiceName("my-nginx-5dc4865748-no-pod")).thenReturn(
            null);
        when(K8sInfoRegistry.getInstance().findServiceName("my-nginx-5dc4865748-no-service")).thenReturn(
            null);
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
}