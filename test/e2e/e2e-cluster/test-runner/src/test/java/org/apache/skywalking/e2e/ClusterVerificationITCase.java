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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class ClusterVerificationITCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVerificationITCase.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final Yaml yaml = new Yaml();

    private SimpleQueryClient queryClient;
    private String instrumentedServiceUrl;
    private long retryInterval = TimeUnit.MINUTES.toMillis(1);

    @Before
    public void setUp() {
        final String swWebappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String swWebappPort = System.getProperty("sw.webapp.port", "32791");
        final String instrumentedServiceHost = System.getProperty("service.host", "127.0.0.1");
        final String instrumentedServicePort = System.getProperty("service.port", "32790");
        queryClient = new SimpleQueryClient(swWebappHost, swWebappPort);
        instrumentedServiceUrl = "http://" + instrumentedServiceHost + ":" + instrumentedServicePort;
    }

    @Test(timeout = 1200000)
    @DirtiesContext
    public void verify() throws Exception {
        LocalDateTime startTime = LocalDateTime.now(ZoneOffset.UTC);

        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        List<Service> services = Collections.emptyList();
        while (services.size() < 2) {
            try {
                restTemplate.postForEntity(
                    instrumentedServiceUrl + "/e2e/users",
                    user,
                    String.class
                );
                services = queryClient.services(
                    new ServicesQuery()
                        .start(startTime)
                        .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                );
            } catch (Throwable ignored) {
            }
        }

        final ResponseEntity<String> responseEntity = restTemplate.postForEntity(
            instrumentedServiceUrl + "/e2e/users",
            user,
            String.class
        );
        LOGGER.info("responseEntity: {}, {}", responseEntity.getStatusCode(), responseEntity.getBody());
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        verifyTraces(startTime);

        verifyServices(startTime);

        verifyTopo(startTime);
    }

    private void verifyTopo(LocalDateTime minutesAgo) throws Exception {
        final TopoData topoData = queryClient.topo(
            new TopoQuery()
                .stepByMinute()
                .start(minutesAgo)
                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
        );

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.topo.yml").getInputStream();

        final TopoMatcher topoMatcher = yaml.loadAs(expectedInputStream, TopoMatcher.class);
        topoMatcher.verify(topoData);
    }

    private void verifyServices(LocalDateTime minutesAgo) throws Exception {
        List<Service> services = queryClient.services(
            new ServicesQuery()
                .start(minutesAgo)
                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
        );
        while (services.isEmpty()) {
            LOGGER.warn("services is null, will retry to query");
            services = queryClient.services(
                new ServicesQuery()
                    .start(minutesAgo)
                    .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
            );
            Thread.sleep(retryInterval);
        }

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.services.yml").getInputStream();

        final ServicesMatcher servicesMatcher = yaml.loadAs(expectedInputStream, ServicesMatcher.class);
        servicesMatcher.verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service, minutesAgo);

            Instances instances = verifyServiceInstances(minutesAgo, service);

            verifyInstancesMetrics(instances, minutesAgo);

            Endpoints endpoints = verifyServiceEndpoints(minutesAgo, service);

            verifyEndpointsMetrics(endpoints, minutesAgo);
        }
    }

    private Instances verifyServiceInstances(LocalDateTime minutesAgo, Service service) throws Exception {
        Instances instances = queryClient.instances(
            new InstancesQuery()
                .serviceId(service.getKey())
                .start(minutesAgo)
                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
        );
        while (instances == null) {
            LOGGER.warn("instances is null, will retry to query");
            instances = queryClient.instances(
                new InstancesQuery()
                    .serviceId(service.getKey())
                    .start(minutesAgo)
                    .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
            );
            Thread.sleep(retryInterval);
        }
        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.instances.yml").getInputStream();
        final InstancesMatcher instancesMatcher = yaml.loadAs(expectedInputStream, InstancesMatcher.class);
        instancesMatcher.verify(instances);
        return instances;
    }

    private Endpoints verifyServiceEndpoints(LocalDateTime minutesAgo, Service service) throws Exception {
        Endpoints endpoints = queryClient.endpoints(
            new EndpointQuery().serviceId(service.getKey())
        );
        while (endpoints == null) {
            LOGGER.warn("endpoints is null, will retry to query");
            endpoints = queryClient.endpoints(
                new EndpointQuery().serviceId(service.getKey())
            );
            Thread.sleep(retryInterval);
        }
        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.endpoints.yml").getInputStream();
        final EndpointsMatcher endpointsMatcher = yaml.loadAs(expectedInputStream, EndpointsMatcher.class);
        endpointsMatcher.verify(endpoints);
        return endpoints;
    }

    private void verifyInstancesMetrics(Instances instances, final LocalDateTime minutesAgo) throws Exception {
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance response time: {}", instance);

                boolean matched = false;
                while (!matched) {
                    LOGGER.warn("instanceRespTime is null, will retry to query");
                    Metrics instanceRespTime = queryClient.metrics(
                            new MetricsQuery()
                                .stepByMinute()
                                .metricsName(metricsName)
                                .start(minutesAgo)
                                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                                .id(instance.getKey())
                        );
                    Thread.sleep(retryInterval);
                    AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    try {
                        instanceRespTimeMatcher.verify(instanceRespTime);
                        matched = true;
                    } catch (Throwable ignored) {
                    }
                    LOGGER.info("{}: {}", metricsName, instanceRespTime);
                }
            }
        }
    }

    private void verifyEndpointsMetrics(Endpoints endpoints, final LocalDateTime minutesAgo) throws Exception {
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            if (!endpoint.getLabel().equals("/e2e/users")) {
                continue;
            }
            for (String metricName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}, metrics: {}", endpoint, metricName);

                boolean matched = false;
                while (!matched) {
                    LOGGER.warn("serviceMetrics is null, will retry to query");
                    Metrics metrics = queryClient.metrics(
                        new MetricsQuery()
                            .stepByMinute()
                            .metricsName(metricName)
                            .start(minutesAgo)
                            .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                            .id(endpoint.getKey())
                    );
                    Thread.sleep(retryInterval);
                    AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    try {
                        instanceRespTimeMatcher.verify(metrics);
                        matched = true;
                    } catch (Throwable ignored) {
                    }
                    LOGGER.info("metrics: {}", metrics);
                }
            }
        }
    }

    private void verifyServiceMetrics(Service service, final LocalDateTime minutesAgo) throws Exception {
        for (String metricName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);

            boolean matched = false;
            while (!matched) {
                Metrics serviceMetrics = queryClient.metrics(
                    new MetricsQuery()
                        .stepByMinute()
                        .metricsName(metricName)
                        .start(minutesAgo)
                        .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                        .id(service.getKey())
                );
                Thread.sleep(retryInterval);
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                try {
                    instanceRespTimeMatcher.verify(serviceMetrics);
                    matched = true;
                } catch (Throwable ignored) {
                }
                LOGGER.info("serviceMetrics: {}", serviceMetrics);
            }
        }
    }

    private void verifyTraces(LocalDateTime minutesAgo) throws Exception {
        List<Trace> traces = queryClient.traces(
            new TracesQuery()
                .stepBySecond()
                .start(minutesAgo)
                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                .orderByStartTime()
        );
        while (traces.isEmpty()) {
            LOGGER.warn("traces is empty, will retry to query");
            traces = queryClient.traces(
                new TracesQuery()
                    .stepBySecond()
                    .start(minutesAgo)
                    .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                    .orderByStartTime()
            );
            Thread.sleep(retryInterval);
        }

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.traces.yml").getInputStream();

        final TracesMatcher tracesMatcher = yaml.loadAs(expectedInputStream, TracesMatcher.class);
        tracesMatcher.verifyLoosely(traces);
    }
}
