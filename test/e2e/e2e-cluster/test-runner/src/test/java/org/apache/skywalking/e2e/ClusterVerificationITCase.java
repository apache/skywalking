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

import static org.apache.skywalking.e2e.metrics.MetricsQuery.*;

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
    private long retryInterval = TimeUnit.SECONDS.toMillis(30);

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

        // minimum guarantee that the instrumented services registered
        // which is the prerequisite of following verifications(service instance, service metrics, etc.)
        List<Service> services = Collections.emptyList();
        while (services.size() < 2) {
            try {
                services = queryClient.services(
                    new ServicesQuery()
                        .start(startTime)
                        .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                );
                Thread.sleep(500); // take a nap to avoid high payload
            } catch (Throwable ignored) {
            }
        }

        verifyTraces(startTime);

        verifyServices(startTime);

        verifyTopo(startTime);
    }

    private void verifyTopo(LocalDateTime minutesAgo) throws Exception {
        boolean valid = false;
        while (!valid) {
            try {
                final TopoData topoData = queryClient.topo(
                        new TopoQuery()
                                .stepByMinute()
                                .start(minutesAgo)
                                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                );
                LOGGER.info("Actual topology: {}", topoData);

                InputStream expectedInputStream =
                        new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.topo.yml").getInputStream();

                final TopoMatcher topoMatcher = yaml.loadAs(expectedInputStream, TopoMatcher.class);
                topoMatcher.verify(topoData);
                valid = true;
            } catch (Throwable t) {
                LOGGER.warn(t.getMessage(), t);
                generateTraffic();
                Thread.sleep(retryInterval);
            }
        }
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
                    .end(LocalDateTime.now(ZoneOffset.UTC))
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
            LOGGER.warn("instances is null, will send traffic data and retry to query");
            generateTraffic();
            Thread.sleep(retryInterval);
            instances = queryClient.instances(
                new InstancesQuery()
                    .serviceId(service.getKey())
                    .start(minutesAgo)
                    .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
            );
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
            LOGGER.warn("endpoints is null, will send traffic data and retry to query");
            generateTraffic();
            Thread.sleep(retryInterval);
            endpoints = queryClient.endpoints(
                new EndpointQuery().serviceId(service.getKey())
            );
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

                boolean valid = false;
                while (!valid) {
                    LOGGER.warn("instanceMetrics is null, will retry to query");
                    Metrics instanceMetrics = queryClient.metrics(
                            new MetricsQuery()
                                .stepByMinute()
                                .metricsName(metricsName)
                                .start(minutesAgo)
                                .end(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))
                                .id(instance.getKey())
                        );
                    AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    try {
                        instanceRespTimeMatcher.verify(instanceMetrics);
                        valid = true;
                    } catch (Throwable ignored) {
                        generateTraffic();
                        Thread.sleep(retryInterval);
                    }
                    LOGGER.info("{}: {}", metricsName, instanceMetrics);
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

                boolean valid = false;
                while (!valid) {
                    Metrics endpointMetrics = queryClient.metrics(
                        new MetricsQuery()
                            .stepByMinute()
                            .metricsName(metricName)
                            .start(minutesAgo)
                            .end(LocalDateTime.now(ZoneOffset.UTC))
                            .id(endpoint.getKey())
                    );
                    AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    try {
                        instanceRespTimeMatcher.verify(endpointMetrics);
                        valid = true;
                    } catch (Throwable ignored) {
                        generateTraffic();
                        Thread.sleep(retryInterval);
                    }
                    LOGGER.info("{}: {}", metricName, endpointMetrics);
                }
            }
        }
    }

    private void verifyServiceMetrics(Service service, final LocalDateTime minutesAgo) throws Exception {
        for (String metricName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);

            boolean valid = false;
            while (!valid) {
                Metrics serviceMetrics = queryClient.metrics(
                    new MetricsQuery()
                        .stepByMinute()
                        .metricsName(metricName)
                        .start(minutesAgo)
                        .end(LocalDateTime.now(ZoneOffset.UTC))
                        .id(service.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                try {
                    instanceRespTimeMatcher.verify(serviceMetrics);
                    valid = true;
                } catch (Throwable ignored) {
                    generateTraffic();
                    Thread.sleep(retryInterval);
                }
                LOGGER.info("{}: {}", metricName, serviceMetrics);
            }
        }
    }

    private void verifyTraces(LocalDateTime minutesAgo) throws Exception {
        final TracesQuery query = new TracesQuery()
            .stepBySecond()
            .start(minutesAgo)
            .orderByStartTime();

        List<Trace> traces = queryClient.traces(query.end(LocalDateTime.now(ZoneOffset.UTC)));
        while (traces.isEmpty()) {
            LOGGER.warn("traces is empty, will generate traffic data and retry");
            generateTraffic();
            Thread.sleep(retryInterval);
            traces = queryClient.traces(query.end(LocalDateTime.now(ZoneOffset.UTC)));
        }

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.traces.yml").getInputStream();

        final TracesMatcher tracesMatcher = yaml.loadAs(expectedInputStream, TracesMatcher.class);
        tracesMatcher.verifyLoosely(traces);
    }

    private void generateTraffic() {
        try {
            final Map<String, String> user = new HashMap<>();
            user.put("name", "SkyWalking");
            final ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                    instrumentedServiceUrl + "/e2e/users",
                    user,
                    String.class
            );
            LOGGER.info("responseEntity: {}, {}", responseEntity.getStatusCode(), responseEntity.getBody());
        } catch (Throwable t) {
            LOGGER.warn(t.getMessage(), t);
        }
    }
}
