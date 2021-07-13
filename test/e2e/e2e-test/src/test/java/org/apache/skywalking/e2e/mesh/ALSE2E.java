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
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.base.TrafficController;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoint;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instance;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.topo.Call;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.topo.Topology;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ALSE2E extends SkyWalkingTestAdapter {
    private final String swWebappHost = Optional.ofNullable(Strings.emptyToNull(System.getenv("WEBAPP_HOST"))).orElse("127.0.0.1");

    private final String swWebappPort = Optional.ofNullable(Strings.emptyToNull(System.getenv("WEBAPP_PORT"))).orElse("12800");

    protected HostAndPort swWebappHostPort = HostAndPort.builder()
                                                        .host(swWebappHost)
                                                        .port(Integer.parseInt(swWebappPort))
                                                        .build();

    private final Map<Predicate<Service>, String> serviceEndpointExpectedDataFiles =
        ImmutableMap.<Predicate<Service>, String>builder()
            .put(service -> service.getLabel().contains("ratings"), "expected/als/endpoints-ratings.yml")
            .put(service -> service.getLabel().contains("details"), "expected/als/endpoints-details.yml")
            .put(service -> service.getLabel().contains("reviews"), "expected/als/endpoints-reviews.yml")
            .put(service -> service.getLabel().contains("productpage"), "expected/als/endpoints-productpage.yml")
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
    void services() throws Exception {
        LOGGER.info("services starts");

        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        LOGGER.info("services: {}", services);

        load("expected/als/services.yml").as(ServicesMatcher.class).verify(services);

        for (final Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    @RetryableTest
    void topology() throws Exception {
        LOGGER.info("topology starts {} {}", graphql, startTime);

        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/als/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());
    }

    private Instances verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {}", instances);

        String file = "expected/als/instances.yml";
        load(file).as(InstancesMatcher.class).verify(instances);

        return instances;
    }

    private Endpoints verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {}", endpoints);

        serviceEndpointExpectedDataFiles.entrySet()
                                        .stream()
                                        .filter(it -> it.getKey().test(service))
                                        .findFirst()
                                        .map(Map.Entry::getValue)
                                        .ifPresent(expectedDataFile -> {
                                            try {
                                                load(expectedDataFile).as(EndpointsMatcher.class).verify(endpoints);
                                            } catch (IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });

        return endpoints;
    }

    private void verifyInstancesMetrics(final Instances instances) throws Exception {
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance response time: {}", instance);
                final Metrics instanceMetrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricsName).id(instance.getKey())
                );

                LOGGER.info("instance metrics: {}", instanceMetrics);

                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceMetrics);
                LOGGER.info("{}: {}", metricsName, instanceMetrics);
            }
        }
    }

    private void verifyEndpointsMetrics(final Endpoints endpoints) throws Exception {
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            for (final String metricName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}: {}", endpoint, metricName);

                final Metrics metrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricName).id(endpoint.getKey())
                );

                LOGGER.info("metrics: {}", metrics);

                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(metrics);

                LOGGER.info("{}: {}", metricName, metrics);
            }
        }
    }

    private void verifyServiceMetrics(final Service service) throws Exception {
        for (String metricName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);
            final Metrics serviceMetrics = graphql.metrics(
                new MetricsQuery().stepByMinute().metricsName(metricName).id(service.getKey())
            );
            LOGGER.info("serviceMetrics: {}", serviceMetrics);
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(serviceMetrics);
            LOGGER.info("{}: {}", metricName, serviceMetrics);
        }
    }

    private void verifyServiceRelationMetrics(final List<Call> calls) throws Exception {
        verifyRelationMetrics(calls, ALL_SERVICE_RELATION_CLIENT_METRICS, ALL_SERVICE_RELATION_SERVER_METRICS);
    }

    private void verifyRelationMetrics(final List<Call> calls,
                                       final String[] relationClientMetrics,
                                       final String[] relationServerMetrics) throws Exception {
        for (Call call : calls) {
            for (String detectPoint : call.getDetectPoints()) {
                switch (detectPoint) {
                    case "CLIENT": {
                        for (String metricName : relationClientMetrics) {
                            verifyMetrics(graphql, metricName, call.getId(), startTime);
                        }
                        break;
                    }
                    case "SERVER": {
                        for (String metricName : relationServerMetrics) {
                            verifyMetrics(graphql, metricName, call.getId(), startTime);
                        }
                        break;
                    }
                }
            }
        }
    }
}
