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

import org.apache.skywalking.e2e.profile.ProfileClient;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationRequest;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationResult;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationResultMatcher;
import org.apache.skywalking.e2e.profile.query.ProfileTaskQuery;
import org.apache.skywalking.e2e.profile.query.ProfileTasks;
import org.apache.skywalking.e2e.profile.query.ProfilesTasksMatcher;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mrpro
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class ProfileVerificationITCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileVerificationITCase.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final int retryInterval = 10;

    private ProfileClient profileClient;
    private String instrumentedServiceUrl;

    @Before
    public void setUp() {
        final String swWebappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        //        final String swWebappPort = System.getProperty("sw.webapp.port", "32783");
        final String swWebappPort = System.getProperty("sw.webapp.port", "12800");
        final String instrumentedServiceHost = System.getProperty("service.host", "127.0.0.1");
        final String instrumentedServicePort = System.getProperty("service.port", "32782");
        //        final String instrumentedServicePort = System.getProperty("service.port", "9090");
        profileClient = new ProfileClient(swWebappHost, swWebappPort);
        instrumentedServiceUrl = "http://" + instrumentedServiceHost + ":" + instrumentedServicePort;
    }

    @Test(timeout = 1200000)
    @DirtiesContext
    public void verify() throws Exception {
        final LocalDateTime minutesAgo = LocalDateTime.now(ZoneOffset.UTC);

        while (true) {
            try {
                final ResponseEntity<String> responseEntity = sendRequest(false);
                LOGGER.info("responseEntity: {}", responseEntity);
                assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                final List<Trace> traces = profileClient.traces(
                        new TracesQuery()
                                .start(minutesAgo)
                                .end(LocalDateTime.now())
                                .orderByDuration()
                );
                if (!traces.isEmpty()) {
                    break;
                }
                Thread.sleep(10000L);
            } catch (Exception ignored) {
            }
        }

        // verify basic info
        verifyServices(minutesAgo);

        // create profile task
        verifyCreateProfileTask(minutesAgo);

    }

    private ResponseEntity<String> sendRequest(boolean needProfiling) {
        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        user.put("enableProfiling", String.valueOf(needProfiling));
        return restTemplate.postForEntity(
                instrumentedServiceUrl + "/e2e/users",
                user,
                String.class
        );
    }

    /**
     * verify create profile task
     * @param minutesAgo
     * @throws Exception
     */
    private void verifyCreateProfileTask(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final ProfileTaskCreationRequest creationRequest = ProfileTaskCreationRequest.builder()
                .serviceId(2)
                .endpointName("/e2e/users")
                .duration(1)
                .startTime(-1)
                .minDurationThreshold(1000)
                .dumpPeriod(50)
                .maxSamplingCount(5).build();

        // verify create task
        final ProfileTaskCreationResult creationResult = profileClient.createProfileTask(creationRequest);
        LOGGER.info("create profile task result: {}", creationResult);

        ProfileTaskCreationResultMatcher creationResultMatcher = new ProfileTaskCreationResultMatcher();
        creationResultMatcher.verify(creationResult);

        // verify get task list and sniffer get task logs
        verifyProfileTask(creationRequest.getServiceId(), "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileTasks.notified.yml");

        // send a profile request
        sendRequest(true);

        // verify task execution finish
        verifyProfileTask(creationRequest.getServiceId(), "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileTasks.finished.yml");
    }

    private void verifyProfileTask(int serviceId, String verifyResources) throws InterruptedException {
        // verify get task list and logs
        for (int i = 0; i < 10; i++) {
            try {
                final ProfileTasks tasks = profileClient.getProfileTaskList(
                        new ProfileTaskQuery()
                                .serviceId(serviceId)
                                .endpointName("")
                );
                LOGGER.info("get profile task list: {}", tasks);

                InputStream expectedInputStream =
                        new ClassPathResource(verifyResources).getInputStream();

                final ProfilesTasksMatcher servicesMatcher = new Yaml().loadAs(expectedInputStream, ProfilesTasksMatcher.class);
                servicesMatcher.verify(tasks);
                break;
            } catch (Throwable e) {
                if (i == 10 - 1) {
                    throw new IllegalStateException("match profile task list fail!", e);
                }
                TimeUnit.SECONDS.sleep(retryInterval);
            }
        }

    }

    private void verifyServices(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Service> services = profileClient.services(
                new ServicesQuery()
                        .start(minutesAgo)
                        .end(now)
        );
        LOGGER.info("services: {}", services);

        InputStream expectedInputStream =
                new ClassPathResource("expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.services.yml").getInputStream();

        final ServicesMatcher servicesMatcher = new Yaml().loadAs(expectedInputStream, ServicesMatcher.class);
        servicesMatcher.verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceInstances(minutesAgo, now, service);

            verifyServiceEndpoints(minutesAgo, now, service);

        }
    }

    private Instances verifyServiceInstances(LocalDateTime minutesAgo, LocalDateTime now,
                                             Service service) throws Exception {
        InputStream expectedInputStream;
        Instances instances = profileClient.instances(
                new InstancesQuery()
                        .serviceId(service.getKey())
                        .start(minutesAgo)
                        .end(now)
        );
        LOGGER.info("instances: {}", instances);
        expectedInputStream =
                new ClassPathResource("expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.instances.yml").getInputStream();
        final InstancesMatcher instancesMatcher = new Yaml().loadAs(expectedInputStream, InstancesMatcher.class);
        instancesMatcher.verify(instances);
        return instances;
    }

    private Endpoints verifyServiceEndpoints(LocalDateTime minutesAgo, LocalDateTime now,
                                             Service service) throws Exception {
        Endpoints instances = profileClient.endpoints(
                new EndpointQuery().serviceId(service.getKey())
        );
        LOGGER.info("instances: {}", instances);
        InputStream expectedInputStream =
                new ClassPathResource("expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.endpoints.yml").getInputStream();
        final EndpointsMatcher endpointsMatcher = new Yaml().loadAs(expectedInputStream, EndpointsMatcher.class);
        endpointsMatcher.verify(instances);
        return instances;
    }

}
