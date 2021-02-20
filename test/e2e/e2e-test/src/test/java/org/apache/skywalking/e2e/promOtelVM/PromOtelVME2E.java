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

package org.apache.skywalking.e2e.promOtelVM;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.UIConfigurationManagementClient;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.ReadLabeledMetricsQuery;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SIMPLE_PROM_VM_LABELED_METERS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SIMPLE_PROM_VM_METERS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class PromOtelVME2E extends SkyWalkingTestAdapter {

    @DockerCompose({"docker/promOtelVM/docker-compose.yml"})
    private DockerComposeContainer<?> compose;

    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    private UIConfigurationManagementClient graphql;

    @BeforeAll
    public void setUp() throws Exception {
        graphql = new UIConfigurationManagementClient(swWebappHostPort.host(), swWebappHostPort.port());

    }

    @RetryableTest
    void testMetrics() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));
        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);
        load("expected/promOtelVM/services.yml").as(ServicesMatcher.class).verify(services);
        Service service = services.get(0);

        for (String metricsName : SIMPLE_PROM_VM_METERS) {
            LOGGER.info("verifying prom vm metrics: {}", metricsName);
            ReadMetrics metrics = graphql.readMetrics(
                new ReadMetricsQuery().stepByMinute()
                                      .metricsName(metricsName)
                                      .serviceName(service.getLabel())
                                      .scope("Service")
                                      .instanceName("")
            );
            LOGGER.info("prom vm metrics: {}", metrics);

            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(metrics.getValues());
        }

        for (Map.Entry<String, List<String>> entry : SIMPLE_PROM_VM_LABELED_METERS.entrySet()) {
            String metricsName = entry.getKey();
            List<String> labels = entry.getValue();
            LOGGER.info("verifying prom vm labeledMetrics: {}", metricsName);
            List<ReadMetrics> labeledMetrics = graphql.readLabeledMetrics(
                new ReadLabeledMetricsQuery().stepByMinute().metricsName(metricsName)
                                             .serviceName(service.getLabel()).scope("Service").instanceName("")
                                             .labels(labels)
            );
            LOGGER.info("prom vm labeledMetrics: {}", labeledMetrics);

            Metrics allValues = new Metrics();
            for (ReadMetrics readMetrics : labeledMetrics) {
                allValues.getValues().addAll(readMetrics.getValues().getValues());
            }
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(allValues);
        }
    }
}
