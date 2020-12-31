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

package org.apache.skywalking.e2e.mesh;

import com.google.common.base.Strings;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.base.TrafficController;
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
import org.junit.jupiter.api.TestInstance;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENVOY_LINER_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.exists;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetricsServiceE2E extends SkyWalkingTestAdapter {
    private final String swWebappHost = Optional.ofNullable(Strings.emptyToNull(System.getenv("WEBAPP_HOST"))).orElse("127.0.0.1");

    private final String swWebappPort = Optional.ofNullable(Strings.emptyToNull(System.getenv("WEBAPP_PORT"))).orElse("12800");

    protected HostAndPort swWebappHostPort = HostAndPort.builder()
                                                        .host(swWebappHost)
                                                        .port(Integer.parseInt(swWebappPort))
                                                        .build();

    @BeforeAll
    public void setUp() throws Exception {
        LOGGER.info("set up");

        queryClient(swWebappHostPort);

        String gatewayHost = Strings.isNullOrEmpty(System.getenv("GATEWAY_HOST")) ? "127.0.0.1" : System.getenv("GATEWAY_HOST");
        String gatewayPort = Strings.isNullOrEmpty(System.getenv("GATEWAY_PORT")) ? "80" : System.getenv("GATEWAY_PORT");

        HostAndPort serviceHostPort = HostAndPort.builder()
                                                 .host(gatewayHost)
                                                 .port(Integer.parseInt(gatewayPort))
                                                 .build();

        final URL url = new URL("http", serviceHostPort.host(), serviceHostPort.port(), "/productpage");

        trafficController =
            TrafficController.builder()
                             .logResult(false)
                             .sender(() -> restTemplate.getForEntity(url.toURI(), String.class))
                             .build()
                             .start();

        LOGGER.info("set up done");
    }

    @RetryableTest
    void test() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        services = services.stream().filter(s -> s.getLabel().startsWith("istio-dp::")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);
        load("expected/metricsservice/services.yml").as(ServicesMatcher.class).verify(services);
        for (final Service service : services) {
            if (service.getLabel().contains("egressgateway")) {
                continue;
            }

            final Instances instances = graphql.instances(
                new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
            );

            LOGGER.info("instances: {}", instances);

            String instancesFile = "expected/metricsservice/instances-" + service.getLabel() + ".yml";
            instancesFile = instancesFile.replaceAll("::", "-");
            if (!exists(instancesFile)) {
                instancesFile = "expected/metricsservice/instances.yml";
            }
            load(instancesFile).as(InstancesMatcher.class).verify(instances);
            for (Instance instance : instances.getInstances()) {
                for (String metricsName : ALL_ENVOY_LINER_METRICS) {
                    LOGGER.info("verifying service instance: {}", instance);
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
