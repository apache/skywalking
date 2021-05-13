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

package org.apache.skywalking.e2e;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.instance.Instance;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SO11Y_LINER_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

/**
 * A test case for simple functionality verification
 *
 * The components are typically:
 *
 * - an single agent, (provider), generating traffic data
 *
 * - an OAP node, (oap)
 *
 * - a webapp, (ui) for querying
 *
 * The verifications are:
 *
 * - services
 *
 * - services metrics
 *
 * - services relations
 *
 * - endpoints
 *
 * - endpoints metrics
 *
 * - instances
 *
 * - instance metrics
 *
 * - topology
 *
 * - traces
 *
 * if your case needs the same aforementioned verifications, consider simply provide a docker-compose.yml with the specific orchestration and reuse these codes.
 */
@Slf4j
@SkyWalkingE2E
public class SO11yE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose("docker/simple/so11y/docker-compose.yml")
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    protected HostAndPort swWebappHostPort;

    @BeforeAll
    void setUp() {
        queryClient(swWebappHostPort);
    }

    @RetryableTest
    void so11y() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        services = services.stream().filter(s -> s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);
        load("expected/simple/so11y-services.yml").as(ServicesMatcher.class).verify(services);
        for (final Service service : services) {
            final Instances instances = graphql.instances(
                new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
            );

            LOGGER.info("instances: {}", instances);

            load("expected/simple/so11y-instances.yml").as(InstancesMatcher.class).verify(instances);
            for (Instance instance : instances.getInstances()) {
                for (String metricsName : ALL_SO11Y_LINER_METRICS) {
                    LOGGER.info("verifying service instance response time: {}", instance);
                    final ReadMetrics instanceMetrics = graphql.readMetrics(
                        new ReadMetricsQuery().stepByMinute().metricsName(metricsName)
                                              .serviceName(service.getLabel()).instanceName(instance.getLabel())
                    );

                    LOGGER.info("{}: {}", metricsName, instanceMetrics);
                    final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    instanceRespTimeMatcher.verify(instanceMetrics.getValues());
                }
            }
        }
    }
}
