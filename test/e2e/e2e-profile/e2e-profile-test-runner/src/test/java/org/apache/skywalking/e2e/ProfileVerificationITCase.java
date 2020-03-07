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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import org.apache.skywalking.e2e.profile.ProfileClient;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        final String swWebappPort = System.getProperty("sw.webapp.port", "12800");
        final String instrumentedServiceHost = System.getProperty("service.host", "127.0.0.1");
        final String instrumentedServicePort = System.getProperty("service.port", "9090");
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
                    new TracesQuery().start(minutesAgo).end(LocalDateTime.now()).orderByDuration());
                LOGGER.info("query traces: {}", traces);
                if (!traces.isEmpty()) {
                    break;
                }
                Thread.sleep(10000L);
            } catch (Exception ignored) {
            }
        }

        // verify basic info
        doRetryableVerification(() -> {
            try {
                verifyServices(minutesAgo);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        });

        // create profile task
        verifyCreateProfileTask(minutesAgo);
    }

    private ResponseEntity<String> sendRequest(boolean needProfiling) {
        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        user.put("enableProfiling", String.valueOf(needProfiling));
        return restTemplate.postForEntity(instrumentedServiceUrl + "/e2e/users", user, String.class);
    }

    /**
     * verify create profile task
     *
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
                                                                                     .minDurationThreshold(1500)
                                                                                     .dumpPeriod(500)
                                                                                     .maxSamplingCount(5)
                                                                                     .build();

        // verify create task
        final ProfileTaskCreationResult creationResult = profileClient.createProfileTask(creationRequest);
        LOGGER.info("create profile task result: {}", creationResult);

        ProfileTaskCreationResultMatcher creationResultMatcher = new ProfileTaskCreationResultMatcher();
        creationResultMatcher.verify(creationResult);

        // verify get task list and sniffer get task logs
        verifyProfileTask(
            creationRequest.getServiceId(),
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileTasks.notified.yml"
        );

        // send a profile request
        sendRequest(true);

        // verify task execution finish
        verifyProfileTask(
            creationRequest.getServiceId(),
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileTasks.finished.yml"
        );

        // verify profiled segment
        verifyProfiledSegment(creationResult.getId());
    }

    private void verifyProfiledSegment(String taskId) throws InterruptedException, IOException {
        // found trace
        Trace foundedTrace = null;
        for (int i = 0; i < 10; i++) {
            try {
                List<Trace> traces = profileClient.getProfiledTraces(taskId);
                LOGGER.info("get profiled segemnt list: {}", traces);

                InputStream expectedInputStream = new ClassPathResource(
                    "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileSegments.yml").getInputStream();
                final TracesMatcher tracesMatcher = new Yaml().loadAs(expectedInputStream, TracesMatcher.class);
                tracesMatcher.verifyLoosely(traces);
                foundedTrace = traces.get(0);
                break;

            } catch (Exception e) {
                if (i == 10 - 1) {
                    throw new IllegalStateException("match profiled segment list fail!", e);
                }
                TimeUnit.SECONDS.sleep(retryInterval);
            }
        }

        String segmentId = foundedTrace.getKey();

        // verify segment
        ProfiledSegment.ProfiledSegmentData segmentData = profileClient.getProfiledSegment(foundedTrace.getKey());
        LOGGER.info("get profiled segment : {}", segmentData);
        InputStream inputStream = new ClassPathResource(
                "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileSegment.yml").getInputStream();
        final ProfiledSegmentMatcher tracesMatcher = new Yaml().loadAs(inputStream, ProfiledSegmentMatcher.class);
        tracesMatcher.verify(segmentData);

        long start = Long.parseLong(foundedTrace.getStart());
        long end = start + foundedTrace.getDuration();
        ProfileAnalyzation analyzation = profileClient.getProfileAnalyzation(segmentId, start, end);
        LOGGER.info("get profile analyzation : {}", analyzation);
        InputStream expectedInputStream = new ClassPathResource(
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.profileAnayzation.yml").getInputStream();
        final ProfileStackTreeMatcher servicesMatcher = new Yaml().loadAs(
            expectedInputStream, ProfileStackTreeMatcher.class);
        servicesMatcher.verify(analyzation.getData().getTrees().get(0));

        // verify shell exporter
        String swHome = System.getProperty("sw.home");
        copyStorageConfig(swHome);
        String exporterBin = swHome + File.separator + "tools" + File.separator + "profile-exporter" + File.separator + "profile_exporter.sh";
        validateExporter(exporterBin, swHome, taskId, foundedTrace.getTraceIds().get(0));
    }

    private void copyStorageConfig(String swHome) throws IOException {
        String runtimeConfigPath = swHome + File.separator + "config" + File.separator + "application.yml";
        String toolConfigPath = swHome + File.separator + "tools" + File.separator + "profile-exporter" + File.separator + "application.yml";

        LOGGER.info("ready to copy storage config, from:{}, to:{}", runtimeConfigPath, toolConfigPath);

        // reading e2e test config
        List<String> runtimeStorageConfigLines = new ArrayList<>();
        boolean currentInStorageConfig = false;
        for (String runtimeConfigLine : Files.readAllLines(new File(runtimeConfigPath).toPath())) {
            if (!currentInStorageConfig) {
                currentInStorageConfig = runtimeConfigLine.startsWith("storage:");
            } else if (runtimeConfigLine.matches("^\\S+\\:$")) {
                currentInStorageConfig = false;
            } else if (!runtimeConfigLine.startsWith("#")) {
                runtimeStorageConfigLines.add(runtimeConfigLine);
            }
        }
        assertThat(runtimeStorageConfigLines).isNotEmpty();
        LOGGER.info("current e2e test storage config:");
        runtimeStorageConfigLines.add(0, "storage:");
        for (String storageLine : runtimeStorageConfigLines) {
            LOGGER.info(storageLine);
        }
        LOGGER.info("------------");

        // copy storage to tools config file
        List<String> newToolConfigLines = new ArrayList<>();
        for (String runtimeConfigLine : Files.readAllLines(new File(toolConfigPath).toPath())) {
            if (!currentInStorageConfig) {
                currentInStorageConfig = runtimeConfigLine.startsWith("storage:");
            } else if (runtimeConfigLine.matches("^\\S+\\:$")) {
                currentInStorageConfig = false;
            }

            if (!currentInStorageConfig) {
                newToolConfigLines.add(runtimeConfigLine);
            }
        }
        newToolConfigLines.addAll(runtimeStorageConfigLines);
        LOGGER.info("copy to tools config file content:");
        for (String storageLine : newToolConfigLines) {
            LOGGER.info(storageLine);
        }
        LOGGER.info("------------");

        // write new config content
        Files.write(new File(toolConfigPath).toPath(), newToolConfigLines, Charsets.UTF_8);
    }

    private void verifyProfileTask(int serviceId, String verifyResources) throws InterruptedException {
        // verify get task list and logs
        for (int i = 0; i < 10; i++) {
            try {
                final ProfileTasks tasks = profileClient.getProfileTaskList(
                    new ProfileTaskQuery().serviceId(serviceId).endpointName(""));
                LOGGER.info("get profile task list: {}", tasks);

                InputStream expectedInputStream = new ClassPathResource(verifyResources).getInputStream();

                final ProfilesTasksMatcher servicesMatcher = new Yaml().loadAs(
                    expectedInputStream, ProfilesTasksMatcher.class);
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

        final List<Service> services = profileClient.services(new ServicesQuery().start(minutesAgo).end(now));
        LOGGER.info("services: {}", services);

        InputStream expectedInputStream = new ClassPathResource(
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.services.yml").getInputStream();

        final ServicesMatcher servicesMatcher = new Yaml().loadAs(expectedInputStream, ServicesMatcher.class);
        servicesMatcher.verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceInstances(minutesAgo, now, service);

            verifyServiceEndpoints(minutesAgo, now, service);

        }
    }

    private Instances verifyServiceInstances(LocalDateTime minutesAgo,
                                             LocalDateTime now,
                                             Service service) throws Exception {
        InputStream expectedInputStream;
        Instances instances = profileClient.instances(
            new InstancesQuery().serviceId(service.getKey()).start(minutesAgo).end(now));
        LOGGER.info("instances: {}", instances);
        expectedInputStream = new ClassPathResource(
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.instances.yml").getInputStream();
        final InstancesMatcher instancesMatcher = new Yaml().loadAs(expectedInputStream, InstancesMatcher.class);
        instancesMatcher.verify(instances);
        return instances;
    }

    private Endpoints verifyServiceEndpoints(LocalDateTime minutesAgo,
                                             LocalDateTime now,
                                             Service service) throws Exception {
        Endpoints instances = profileClient.endpoints(new EndpointQuery().serviceId(service.getKey()));
        LOGGER.info("instances: {}", instances);
        InputStream expectedInputStream = new ClassPathResource(
            "expected-data/org.apache.skywalking.e2e.ProfileVerificationITCase.endpoints.yml").getInputStream();
        final EndpointsMatcher endpointsMatcher = new Yaml().loadAs(expectedInputStream, EndpointsMatcher.class);
        endpointsMatcher.verify(instances);
        return instances;
    }

    private void doRetryableVerification(Runnable runnable) throws InterruptedException {
        while (true) {
            try {
                runnable.run();
                break;
            } catch (Throwable ignored) {
                Thread.sleep(retryInterval);
            }
        }
    }

    private void validateExporter(String exporterBin, String exportTo, String taskId, String traceId) throws IOException, InterruptedException {
        String executeShell = exporterBin;
        executeShell += " --taskid=" + taskId;
        executeShell += " --traceid=" + traceId;
        executeShell += " " + exportTo;

        LOGGER.info("executing shell: {}", executeShell);

        Properties properties = System.getProperties();
        List<String> env = properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());
        Process exec = Runtime.getRuntime().exec(executeShell, env.toArray(new String[env.size()]));

        // print data
        BufferedReader strCon = new BufferedReader(new InputStreamReader(exec.getInputStream()));
        String line;
        while ((line = strCon.readLine()) != null) {
            LOGGER.info("executing: {}", line);
        }
        exec.waitFor();

        // reading result file
        File zipFile = new File(exportTo + File.separator + traceId + ".tar.gz");
        assertThat(zipFile).canRead();

        // delete it
        zipFile.delete();
    }

}
