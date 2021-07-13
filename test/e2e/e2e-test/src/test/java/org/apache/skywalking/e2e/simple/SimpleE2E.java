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

package org.apache.skywalking.e2e.simple;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.instance.Instances;
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
 * if your case needs the same aforementioned verifications, consider simply provide a docker-compose.yml with the
 * specific orchestration and reuse these codes.
 */
@Slf4j
@SkyWalkingE2E
public class SimpleE2E extends SimpleE2EBase {
    @SuppressWarnings("unused")
    @DockerCompose("docker/simple/${SW_SIMPLE_CASE}/docker-compose.yml")
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    protected HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    protected HostAndPort serviceHostPort;

    @BeforeAll
    void setUp() throws Exception {
        queryClient(swWebappHostPort);

        trafficController(serviceHostPort, "/users");
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

        load("expected/simple/services.yml").as(ServicesMatcher.class).verify(services);

        for (final Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            verifyInstancesJVMMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    @RetryableTest
    void traces() throws Exception {
        final List<Trace> traces = graphql.traces(new TracesQuery().start(startTime).end(now()).orderByDuration());

        LOGGER.info("traces: {}", traces);

        load("expected/simple/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }

    @RetryableTest
    void topology() throws Exception {
        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/simple/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());
    }

    @RetryableTest
    void serviceInstances() throws Exception {
        final ServiceInstanceTopology topology = graphql.serviceInstanceTopo(
            new ServiceInstanceTopologyQuery().stepByMinute()
                                              .start(startTime.minusDays(1))
                                              .end(now())
                                              .clientServiceId("VXNlcg==.0")
                                              .serverServiceId("WW91cl9BcHBsaWNhdGlvbk5hbWU=.1"));

        LOGGER.info("instance topology: {}", topology);

        load("expected/simple/serviceInstanceTopo.yml").as(ServiceInstanceTopologyMatcher.class).verify(topology);

        verifyServiceInstanceRelationMetrics(topology.getCalls());
    }
}
