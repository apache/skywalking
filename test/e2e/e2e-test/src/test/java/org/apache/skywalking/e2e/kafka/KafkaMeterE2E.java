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

package org.apache.skywalking.e2e.kafka;

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
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SIMPLE_MICROMETER_METERS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class KafkaMeterE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/kafka/docker-compose.yml",
        "docker/kafka/docker-compose.meter.yml"
    })
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    protected HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider_kafka", port = 9090)
    protected HostAndPort serviceHostPort;

    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);

        trafficController(serviceHostPort, "/users");
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    public void meters() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        services = services.stream().filter(s -> !s.getLabel().equals("oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/meter/services.yml").as(ServicesMatcher.class).verify(services);

        for (final Service service : services) {
            final Instances instances = verifyServiceInstances(service);
            for (String meterName : SIMPLE_MICROMETER_METERS) {
                LOGGER.info("verifying service {}, metrics: {}", service, meterName);
                final ReadMetrics instanceMetrics = graphql.readMetrics(
                    new ReadMetricsQuery().stepByMinute().metricsName(meterName).serviceName(service.getLabel())
                                          .instanceName(instances.getInstances().get(0).getLabel())
                );
                LOGGER.info("instanceMetrics: {}", instanceMetrics);
                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceMetrics.getValues());
                LOGGER.info("{}: {}", meterName, instanceMetrics);
            }
        }
    }

    private Instances verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {}", instances);

        load("expected/meter/instances.yml").as(InstancesMatcher.class).verify(instances);

        return instances;
    }
}
