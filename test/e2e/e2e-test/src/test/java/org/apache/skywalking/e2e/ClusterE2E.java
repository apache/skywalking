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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
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
import static org.assertj.core.api.Assertions.fail;

@Slf4j
@SkyWalkingE2E
public class ClusterE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/cluster/docker-compose.yml",
        "docker/cluster/docker-compose.${SW_COORDINATOR}.yml",
        "docker/cluster/docker-compose.${SW_COORDINATOR}.${SW_STORAGE}.yml",
    })
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "consumer", port = 9092)
    private HostAndPort serviceHostPort;

    private final String providerName = "e2e-service-provider";
    private final String consumerName = "e2e-service-consumer";

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
    void traces() throws Exception {
        final TracesQuery query = new TracesQuery().stepBySecond().start(startTime).orderByStartTime();
        final List<Trace> traces = graphql.traces(query.end(now()));

        LOGGER.info("traces: {}", traces);

        load("expected/cluster/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }

    @RetryableTest
    void topology() throws Exception {
        final Topology topology = graphql.topo(
            new TopoQuery().stepByMinute().start(startTime).end(now().plusMinutes(1))
        );

        LOGGER.info("topology: {}", topology);

        load("expected/cluster/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());

        final String clientId = topology.getNodes()
                                        .stream()
                                        .filter(n -> n.getName().equals(providerName))
                                        .findFirst()
                                        .orElseThrow(NullPointerException::new)
                                        .getId();

        final String serverId = topology.getNodes()
                                        .stream()
                                        .filter(n -> n.getName().equals(consumerName))
                                        .findFirst()
                                        .orElseThrow(NullPointerException::new)
                                        .getId();

        verifyServiceInstanceTopo(clientId, serverId);
    }

    private void verifyServiceInstanceTopo(final String clientId, final String serverId) throws Exception {
        if (clientId == null || serverId == null) {
            fail("clientService or serverService not found");
        }

        final ServiceInstanceTopology topology = graphql.serviceInstanceTopo(
            new ServiceInstanceTopologyQuery().stepByMinute()
                                              .start(startTime.minusDays(1))
                                              .end(now().plusMinutes(1))
                                              .clientServiceId(clientId)
                                              .serverServiceId(serverId));

        LOGGER.info("Actual service instance topology: {}", topology);

        load("expected/cluster/serviceInstanceTopo.yml").as(ServiceInstanceTopologyMatcher.class).verify(topology);

        verifyServiceInstanceRelationMetrics(topology.getCalls());
    }

    @RetryableTest
    void services() throws Exception {
        final List<Service> services = graphql.services(
            new ServicesQuery().start(startTime).end(now().plusMinutes(1))
        );

        load("expected/cluster/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    private Instances verifyServiceInstances(Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now().plusMinutes(1))
        );

        if (providerName.equals(service.getLabel())) {
            load("expected/cluster/providerInstances.yml").as(InstancesMatcher.class).verify(instances);
        } else {
            load("expected/cluster/instances.yml").as(InstancesMatcher.class).verify(instances);
        }

        return instances;
    }

    private Endpoints verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {}", endpoints);

        load("expected/cluster/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);

        return endpoints;
    }

    private void verifyInstancesMetrics(final Instances instances) throws Exception {
        for (final Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance response time: {}", instance);

                verifyMetrics(graphql, metricsName, instance.getKey(), startTime);
            }
        }
    }

    private void verifyEndpointsMetrics(final Endpoints endpoints) throws Exception {
        for (final Endpoint endpoint : endpoints.getEndpoints()) {
            if (!endpoint.getLabel().equals("/users")) {
                continue;
            }
            for (String metricName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}, metrics: {}", endpoint, metricName);

                verifyMetrics(graphql, metricName, endpoint.getKey(), startTime);
            }

            for (String metricName : ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS) {
                verifyPercentileMetrics(graphql, metricName, endpoint.getKey(), startTime);
            }
        }
    }

    private void verifyServiceMetrics(final Service service) throws Exception {
        for (String metricName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);

            verifyMetrics(graphql, metricName, service.getKey(), startTime);
        }
        for (String metricName : ALL_SERVICE_MULTIPLE_LINEAR_METRICS) {
            verifyPercentileMetrics(graphql, metricName, service.getKey(), startTime);
        }
    }

    private void verifyServiceInstanceRelationMetrics(final List<Call> calls) throws Exception {
        verifyRelationMetrics(
            calls, ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS, ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS
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
