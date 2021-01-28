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

package org.apache.skywalking.e2e.kafka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.ProfileClient;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.annotation.DockerContainer;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationRequest;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationResult;
import org.apache.skywalking.e2e.profile.creation.ProfileTaskCreationResultMatcher;
import org.apache.skywalking.e2e.profile.query.ProfileAnalyzation;
import org.apache.skywalking.e2e.profile.query.ProfileStackTreeMatcher;
import org.apache.skywalking.e2e.profile.query.ProfileTaskQuery;
import org.apache.skywalking.e2e.profile.query.ProfileTasks;
import org.apache.skywalking.e2e.profile.query.ProfiledSegment;
import org.apache.skywalking.e2e.profile.query.ProfiledSegmentMatcher;
import org.apache.skywalking.e2e.profile.query.ProfilesTasksMatcher;
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
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesMatcher;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SkyWalkingE2E
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaProfileE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/kafka/docker-compose.yml",
        "docker/kafka/docker-compose.profiling.yml"
    })
    protected DockerComposeContainer<?> compose;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider_kafka", port = 9090)
    protected HostAndPort serviceHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    protected HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @DockerContainer("oap")
    private ContainerState oapContainer;

    private ProfileClient graphql;
    private String instrumentedServiceUrl;

    @BeforeAll
    public void setUp() {
        graphql = new ProfileClient(swWebappHostPort.host(), swWebappHostPort.port());
        instrumentedServiceUrl = "http://" + serviceHostPort.host() + ":" + serviceHostPort.port();
    }

    @Order(1)
    @RetryableTest
    void traces() throws Exception {
        final ResponseEntity<String> response = sendRequest(false);

        LOGGER.info("response: {}", response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final List<Trace> traces = graphql.traces(
            new TracesQuery().start(startTime).end(now()).orderByDuration()
        );

        LOGGER.info("traces: {}", traces);

        assertThat(traces).isNotEmpty();
    }

    @Order(2)
    @RetryableTest
    void services() throws Exception {
        final List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        LOGGER.info("services: {}", services);

        load("expected/profile/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceInstances(service);

            verifyServiceEndpoints(service);
        }
    }

    @Test
    @Order(3)
    void createProfileTask() throws Exception {
        final ProfileTaskCreationRequest creationRequest = ProfileTaskCreationRequest.builder()
                                                                                     .serviceId("ZTJlLXByb2ZpbGUtc2VydmljZQ==.1")
                                                                                     .endpointName("{POST}/profile/{name}")
                                                                                     .duration(1)
                                                                                     .startTime(-1)
                                                                                     .minDurationThreshold(1500)
                                                                                     .dumpPeriod(500)
                                                                                     .maxSamplingCount(5)
                                                                                     .build();

        final ProfileTaskCreationResult creationResult = graphql.createProfileTask(creationRequest);

        LOGGER.info("create profile task result: {}", creationResult);

        final ProfileTaskCreationResultMatcher creationResultMatcher = new ProfileTaskCreationResultMatcher();
        creationResultMatcher.verify(creationResult);

        verifyProfileTask(creationRequest.getServiceId(), "expected/profile/notified.yml");

        sendRequest(true);

        verifyProfileTask(creationRequest.getServiceId(), "expected/profile/finished.yml");

        verifyProfiledSegment(creationResult.getId());
    }

    private ResponseEntity<String> sendRequest(boolean needProfiling) {
        final Map<String, String> user = ImmutableMap.of(
            "name", "SkyWalking", "enableProfiling", String.valueOf(needProfiling)
        );
        return restTemplate.postForEntity(instrumentedServiceUrl + "/profile/users?e2e=true", user, String.class);
    }

    private void verifyProfiledSegment(String taskId) throws Exception {
        Trace foundedTrace = null;
        for (int i = 0; i < 10; i++) {
            try {
                final List<Trace> traces = graphql.getProfiledTraces(taskId);

                LOGGER.info("get profiled segemnt list: {}", traces);

                load("expected/profile/profileSegments.yml").as(TracesMatcher.class).verifyLoosely(traces);

                foundedTrace = traces.get(0);
                break;
            } catch (Exception e) {
                if (i == 10 - 1) {
                    throw new IllegalStateException("match profiled segment list fail!", e);
                }
                TimeUnit.SECONDS.sleep(30);
            }
        }
        assert foundedTrace != null;
        final String segmentId = foundedTrace.getKey();
        final ProfiledSegment.ProfiledSegmentData segmentData = graphql.getProfiledSegment(foundedTrace.getKey());

        LOGGER.info("get profiled segment : {}", segmentData);

        load("expected/profile/profileSegment.yml").as(ProfiledSegmentMatcher.class).verify(segmentData);

        final long start = Long.parseLong(foundedTrace.getStart());
        final long end = start + foundedTrace.getDuration();

        final ProfileAnalyzation analyzation = graphql.getProfileAnalyzation(segmentId, start, end);

        LOGGER.info("get profile analyzation : {}", analyzation);

        load("expected/profile/profileAnayzation.yml").as(ProfileStackTreeMatcher.class)
                                                      .verify(analyzation.getData().getTrees().get(0));

        validateExporter(taskId, foundedTrace.getTraceIds().get(0));
    }

    private void verifyProfileTask(String serviceId, String verifyResources) throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                final ProfileTasks tasks = graphql.getProfileTaskList(
                    new ProfileTaskQuery().serviceId(serviceId).endpointName("")
                );

                LOGGER.info("get profile task list: {}", tasks);

                load(verifyResources).as(ProfilesTasksMatcher.class).verify(tasks);
                break;
            } catch (Throwable e) {
                if (i == 10 - 1) {
                    throw new IllegalStateException("match profile task list fail!", e);
                }
                TimeUnit.SECONDS.sleep(30);
            }
        }
    }

    private void verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {}", instances);

        load("expected/profile/instances.yml").as(InstancesMatcher.class).verify(instances);

    }

    private void verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {}", endpoints);

        load("expected/profile/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);

    }

    private void validateExporter(final String taskId, final String traceId) throws Exception {
        final String exportShell = String.format(
            "/skywalking/tools/profile-exporter/profile_exporter.sh --taskid=%s --traceid=%s /tmp",
            taskId, traceId
        );
        final Container.ExecResult exportResult = oapContainer.execInContainer("/bin/sh", "-c", exportShell);

        LOGGER.info("exported result: {}", exportResult);

        assertThat(exportResult.getExitCode()).isEqualTo(0);

        final String lsExportedFileShell = String.format("ls /tmp/%s.tar.gz", traceId);
        final Container.ExecResult checkExportedFileResult = oapContainer.execInContainer(
            "/bin/sh", "-c", lsExportedFileShell);

        LOGGER.info("check exported file result: {}", checkExportedFileResult);

        assertThat(checkExportedFileResult.getExitCode()).isEqualTo(0);
    }
}
