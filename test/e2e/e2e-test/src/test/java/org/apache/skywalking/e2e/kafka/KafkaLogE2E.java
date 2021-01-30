/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.apache.skywalking.e2e.log.Log;
import org.apache.skywalking.e2e.log.LogsMatcher;
import org.apache.skywalking.e2e.log.LogsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.utils.Times;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class KafkaLogE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/kafka/docker-compose.yml",
        "docker/kafka/docker-compose.log.yml"
    })
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    private HostAndPort serviceHostPort;

    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);
        trafficController(serviceHostPort, "/logs/trigger");
    }

    @AfterAll
    public void tearDown() {
        if (trafficController != null) {
            trafficController.stop();
        }
    }

    @RetryableTest
    public void verifyService() throws Exception {
        List<Service> services = graphql.services(
            new ServicesQuery().start(startTime).end(Times.now()));
        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/log/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instance: {}", service);
            // instance
            verifyServiceInstances(service);
        }
    }

    @RetryableTest
    public void verifyLog() throws Exception {
        LogsQuery logsQuery = new LogsQuery().serviceId("WW91cl9BcHBsaWNhdGlvbk5hbWU=.1")
                                             .start(startTime)
                                             .end(Times.now());
        if (graphql.supportQueryLogsByKeywords()) {
            logsQuery.keywordsOfContent("now");
        }
        final List<Log> logs = graphql.logs(logsQuery);
        LOGGER.info("logs: {}", logs);

        load("expected/log/logs.yml").as(LogsMatcher.class).verifyLoosely(logs);
    }

    private void verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(Times.now()));

        LOGGER.info("instances: {}", instances);
        load("expected/log/instances.yml").as(InstancesMatcher.class).verify(instances);
    }

    private void verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));
        LOGGER.info("endpoints: {}", endpoints);

        load("expected/log/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);
    }
}
