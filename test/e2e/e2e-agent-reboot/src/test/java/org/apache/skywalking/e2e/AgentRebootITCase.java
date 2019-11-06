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

import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
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
import org.apache.skywalking.e2e.topo.TopoData;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
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

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class AgentRebootITCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRebootITCase.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final int retryInterval = 30000;

    private SimpleQueryClient queryClient;
    private String instrumentedServiceUrl;

    @Before
    public void setUp() {
        final String swWebappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String swWebappPort = System.getProperty("sw.webapp.port", "32783");
        final String instrumentedServiceHost = System.getProperty("client.host", "127.0.0.1");
        final String instrumentedServicePort = System.getProperty("client.port", "32782");
        queryClient = new SimpleQueryClient(swWebappHost, swWebappPort);
        instrumentedServiceUrl = "http://" + instrumentedServiceHost + ":" + instrumentedServicePort;
    }

    @Test(timeout = 1200000)
    @DirtiesContext
    public void verify() throws Exception {
        doVerify();

        LOGGER.info("Verifications passed before restarting");

        restartOAP();

        waitOAPStartUp();

        assertDataErased();

        LOGGER.info("Verifying after restarting successfully");

        doVerify();

        LOGGER.info("Verifications passed after restarting");
    }

    private void doVerify() throws InterruptedException {
        final LocalDateTime minutesAgo = LocalDateTime.now(ZoneOffset.UTC);

        while (true) {
            try {
                final Map<String, String> user = new HashMap<>();
                user.put("name", "SkyWalking");
                final ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                    instrumentedServiceUrl + "/e2e/users",
                    user,
                    String.class
                );
                assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                final List<Trace> traces = queryClient.traces(
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

        doRetryableVerification(() -> {
            try {
                verifyTraces(minutesAgo);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        });

        doRetryableVerification(() -> {
            try {
                verifyServices(minutesAgo);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        });

        doRetryableVerification(() -> {
            try {
                verifyTopo(minutesAgo);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        });
    }

    private void verifyTopo(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final TopoData topoData = queryClient.topo(
            new TopoQuery()
                .stepByMinute()
                .start(minutesAgo.minusDays(1))
                .end(now)
        );

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.topo.yml").getInputStream();

        final TopoMatcher topoMatcher = new Yaml().loadAs(expectedInputStream, TopoMatcher.class);
        topoMatcher.verify(topoData);
    }

    private void verifyServices(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Service> services = queryClient.services(
            new ServicesQuery()
                .start(minutesAgo)
                .end(now)
        );

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.services.yml").getInputStream();

        final ServicesMatcher servicesMatcher = new Yaml().loadAs(expectedInputStream, ServicesMatcher.class);
        servicesMatcher.verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            Instances instances = verifyServiceInstances(minutesAgo, now, service);

            verifyInstancesMetrics(instances);

            Endpoints endpoints = verifyServiceEndpoints(minutesAgo, now, service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    private Instances verifyServiceInstances(LocalDateTime minutesAgo, LocalDateTime now, Service service) throws Exception {
        InputStream expectedInputStream;
        Instances instances = queryClient.instances(
            new InstancesQuery()
                .serviceId(service.getKey())
                .start(minutesAgo)
                .end(now)
        );
        expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.instances.yml").getInputStream();
        final InstancesMatcher instancesMatcher = new Yaml().loadAs(expectedInputStream, InstancesMatcher.class);
        instancesMatcher.verify(instances);
        return instances;
    }

    private Endpoints verifyServiceEndpoints(LocalDateTime minutesAgo, LocalDateTime now, Service service) throws Exception {
        Endpoints instances = queryClient.endpoints(
            new EndpointQuery().serviceId(service.getKey())
        );
        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.endpoints.yml").getInputStream();
        final EndpointsMatcher endpointsMatcher = new Yaml().loadAs(expectedInputStream, EndpointsMatcher.class);
        endpointsMatcher.verify(instances);
        return instances;
    }

    private void verifyInstancesMetrics(Instances instances) throws Exception {
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance {}, metrics {}", instance, metricsName);
                final Metrics instanceMetrics = queryClient.metrics(
                    new MetricsQuery()
                        .stepByMinute()
                        .metricsName(metricsName)
                        .id(instance.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceMetrics);
                LOGGER.info("{}: {}", metricsName, instanceMetrics);
            }
        }
    }

    private void verifyEndpointsMetrics(Endpoints endpoints) throws Exception {
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            if (!endpoint.getLabel().equals("/e2e/users")) {
                continue;
            }
            for (String metricsName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}, metrics: {}", endpoint, metricsName);
                final Metrics metrics = queryClient.metrics(
                    new MetricsQuery()
                        .stepByMinute()
                        .metricsName(metricsName)
                        .id(endpoint.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
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
            final Metrics serviceMetrics = queryClient.metrics(
                new MetricsQuery()
                    .stepByMinute()
                    .metricsName(metricsName)
                    .id(service.getKey())
            );
            AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(serviceMetrics);
            LOGGER.info("{}}: {}", metricsName, serviceMetrics);
        }
    }

    private void verifyTraces(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Trace> traces = queryClient.traces(
            new TracesQuery()
                .start(minutesAgo)
                .end(now)
                .orderByDuration()
        );

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.traces.yml").getInputStream();

        final TracesMatcher tracesMatcher = new Yaml().loadAs(expectedInputStream, TracesMatcher.class);
        tracesMatcher.verifyLoosely(traces);
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

    private void restartOAP() {
        final String rebootHost = System.getProperty("reboot.host", "127.0.0.1");
        final String rebootPort = System.getProperty("reboot.port", "9091");
        try {
            final String url = "http://" + rebootHost + ":" + rebootPort;
            LOGGER.info("restarting OAP: {}", url);
            restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void waitOAPStartUp() {
        for (int i = 0; ; i++) {
            try {
                queryClient.traces(
                    new TracesQuery()
                        .start(LocalDateTime.now())
                        .end(LocalDateTime.now())
                        .orderByDuration()
                );
                break;
            } catch (Throwable e) {
                LOGGER.info("OAP restart not ready, waited {} seconds, {}", i * 10, e.getMessage());
                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void assertDataErased() throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Trace> traces = queryClient.traces(
            new TracesQuery()
                .start(now.minusMinutes(10))
                .end(now)
                .orderByDuration()
        );

        assertThat(traces).isEmpty();
    }
}
