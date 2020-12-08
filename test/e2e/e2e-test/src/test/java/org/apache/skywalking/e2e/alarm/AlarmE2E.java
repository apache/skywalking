/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.alarm;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.DockerComposeContainer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AlarmE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose("docker/alarm/docker-compose.yml")
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    protected HostAndPort swWebappHostPort = HostAndPort.builder().host("localhost").port(12800).build();

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    protected HostAndPort serviceHostPort = HostAndPort.builder().host("localhost").port(9090).build();

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
    @Order(1)
    void services() throws Exception {
        // Load services
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));
        LOGGER.info("services: {}", services);
        load("expected/alarm/services.yml").as(ServicesMatcher.class).verify(services);
    }

    @RetryableTest
    @Order(2)
    void basicAlarm() throws Exception {
        // Wait all alarm notified(single and compose)
        validate("expected/alarm/silence-before-graphql.yml", "expected/alarm/silence-before-webhook.yml");

        // Wait silence period finished
        TimeUnit.SECONDS.sleep(90);
    }

    @RetryableTest
    @Order(3)
    void afterSilenceAlarm() throws Exception {
        // Retry to send request and check silence config
        validate("expected/alarm/silence-after-graphql.yml", "expected/alarm/silence-after-webhook.yml");
    }

    private void validate(String alarmFile, String hookFile) throws Exception {
        // validate graphql
        GetAlarm alarms = graphql.readAlarms(new AlarmQuery().start(startTime).end(now()));
        LOGGER.info("alarms query: {}", alarms);
        load(alarmFile).as(AlarmsMatcher.class).verify(alarms);

        // validate web hook receiver
        ResponseEntity<HookAlarms> responseEntity = restTemplate.postForEntity("http://" + serviceHostPort.host() + ":" + serviceHostPort.port() + "/alarm/read", null, HookAlarms.class);
        LOGGER.info("alarms hook: {}", responseEntity.getBody());
        load(hookFile).as(HookAlarms.class).verify(responseEntity.getBody());
    }

}
