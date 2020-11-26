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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
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
import org.apache.skywalking.e2e.topo.ServiceInstanceTopology;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyMatcher;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyQuery;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.topo.Topology;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesMatcher;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyPercentileMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_MULTIPLE_LINEAR_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class PythonE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose("docker/python/docker-compose.yml")
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "consumer", port = 9090)
    private HostAndPort pythonHostPort;

    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);

        trafficController(pythonHostPort, "/test");
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    void services() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));
        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/python/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            if ("Your_ApplicationName".equals(service.getLabel())) {
                continue;
            }

            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    @RetryableTest
    void traces() throws Exception {
        final List<Trace> traces = graphql.traces(new TracesQuery().start(startTime).end(now()).orderByStartTime());

        LOGGER.info("traces: {}", traces);

        load("expected/python/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }

    @RetryableTest
    void topology() throws Exception {
        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/python/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());
    }

    @RetryableTest
    void serviceInstances() throws Exception {

        final ServiceInstanceTopology topology = graphql.serviceInstanceTopo(
            new ServiceInstanceTopologyQuery().stepByMinute()
                                              .start(startTime.minusDays(1))
                                              .end(now())
                                              .clientServiceId("Y29uc3VtZXI=.1")
                                              .serverServiceId("WW91cl9BcHBsaWNhdGlvbk5hbWU=.1"));

        LOGGER.info("topology: {}", topology);

        load("expected/python/consumer-instance-topo.yml").as(ServiceInstanceTopologyMatcher.class).verify(topology);

        verifyServiceInstanceRelationMetrics(topology.getCalls());
    }

    private Instances verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
                new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {} {}", service.getLabel(), instances);

        load(String.format("expected/python/%s-instances.yml", service.getLabel()))
            .as(InstancesMatcher.class)
            .verify(instances);

        return instances;
    }

    private Endpoints verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {} {}", service.getLabel(), endpoints);

        load(String.format("expected/python/%s-endpoints.yml", service.getLabel()))
            .as(EndpointsMatcher.class)
            .verify(endpoints);

        return endpoints;
    }

    private void verifyInstancesMetrics(Instances instances) throws Exception {
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

    private void verifyEndpointsMetrics(Endpoints endpoints) throws Exception {
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
            for (String metricName : ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS) {
                verifyPercentileMetrics(graphql, metricName, endpoint.getKey(), startTime);
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

        for (String metricName : ALL_SERVICE_MULTIPLE_LINEAR_METRICS) {
            verifyPercentileMetrics(graphql, metricName, service.getKey(), startTime);
        }
    }

    private void verifyServiceInstanceRelationMetrics(final List<Call> calls) throws Exception {
        verifyRelationMetrics(
            calls, ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS,
            ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS
        );
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
