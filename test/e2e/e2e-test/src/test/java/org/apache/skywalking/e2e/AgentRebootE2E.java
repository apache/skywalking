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
import org.apache.skywalking.e2e.annotation.DockerContainer;
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
import org.apache.skywalking.e2e.topo.Topology;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesMatcher;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SkyWalkingE2E
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AgentRebootE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose("docker/agent-reboot/docker-compose.yml")
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    private HostAndPort serviceHostPort;

    @DockerContainer("oap")
    @SuppressWarnings("unused")
    private ContainerState oapContainer;

    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);

        trafficController(serviceHostPort, "/users");
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @Order(1)
    @RetryableTest
    void beforeReboot() throws Exception {
        verifyTraces();

        verifyServices();

        verifyTopology();
    }

    @Order(2)
    @RetryableTest
    @DisplayName("restart OAP")
    void restartOAP() throws Exception {
        final String killCommand = "ps -ef | grep -v grep | grep OAPServerStartUp | awk '{print $2}' | xargs --no-run-if-empty kill";
        final String startCommand = "bash bin/oapService.sh > /dev/null 2>&1 &";
        final Container.ExecResult killResult = oapContainer.execInContainer("/bin/bash", "-c", killCommand);

        LOGGER.info("kill oap result: {}", killResult);

        assertThat(killResult.getExitCode()).isEqualTo(0);

        final Container.ExecResult startResult = oapContainer.execInContainer("/bin/bash", "-c", startCommand);

        LOGGER.info("start oap result: {}", startResult);

        assertThat(startResult.getExitCode()).isEqualTo(0);
    }

    @Order(3)
    @RetryableTest
    @DisplayName("wait for OAP to be ready")
    void waitOAPStartUp() throws Exception {
        graphql.traces(new TracesQuery().start(startTime).end(now()).orderByDuration());
    }

    @Order(4)
    @RetryableTest
    @DisplayName("make sure data is erased after OAP reboots")
    void assertDataErased() throws Exception {
        final List<Trace> traces = graphql.traces(
            new TracesQuery().start(now().minusMinutes(10)).end(now()).orderByDuration()
        );

        assertThat(traces).isEmpty();
    }

    @Order(5)
    @RetryableTest
    void afterReboot() throws Exception {
        verifyTraces();

        verifyServices();

        verifyTopology();
    }

    private void verifyTopology() throws Exception {
        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/agent-reboot/topo.yml").as(TopoMatcher.class).verify(topology);
    }

    private void verifyServices() throws Exception {
        final List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        load("expected/agent-reboot/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    private Instances verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {}", instances);

        load("expected/agent-reboot/instances.yml").as(InstancesMatcher.class).verify(instances);

        return instances;
    }

    private Endpoints verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {}", endpoints);

        load("expected/agent-reboot/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);

        return endpoints;
    }

    private void verifyInstancesMetrics(final Instances instances) throws Exception {
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance {}, metrics {}", instance, metricsName);
                final Metrics instanceMetrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricsName).id(instance.getKey())
                );
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
            if (!endpoint.getLabel().equals("/users")) {
                continue;
            }
            for (String metricsName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}, metrics: {}", endpoint, metricsName);
                final Metrics metrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricsName).id(endpoint.getKey())
                );
                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(metrics);
                LOGGER.info("{}: {}", metricsName, metrics);
            }
        }
    }

    private void verifyServiceMetrics(Service service) throws Exception {
        for (String metricsName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricsName);
            final Metrics serviceMetrics = graphql.metrics(
                new MetricsQuery().stepByMinute().metricsName(metricsName).id(service.getKey())
            );
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(serviceMetrics);
            LOGGER.info("{}}: {}", metricsName, serviceMetrics);
        }
    }

    private void verifyTraces() throws Exception {
        final List<Trace> traces = graphql.traces(new TracesQuery().start(startTime).end(now()).orderByDuration());

        load("expected/agent-reboot/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }
}
