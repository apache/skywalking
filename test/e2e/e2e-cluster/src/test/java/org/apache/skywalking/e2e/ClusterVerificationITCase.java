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

import static org.apache.skywalking.e2e.metrics.MetricsQuery.ENDPOINT_P50;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ENDPOINT_P75;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ENDPOINT_P90;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ENDPOINT_P95;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ENDPOINT_P99;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_INSTANCE_CPM;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_INSTANCE_RESP_TIME;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_INSTANCE_SLA;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_P50;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_P75;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_P90;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_P95;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_P99;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class ClusterVerificationITCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVerificationITCase.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private SimpleQueryClient queryClient;
    private String instrumentedServiceUrl0;
    private String instrumentedServiceUrl1;

    @Before
    public void setUp() {
        final String swWebappHost = System.getProperty("sw.webapp1.host", "127.0.0.1");
        final String swWebappPort = System.getProperty("sw.webapp1.port", "32783");
        final String instrumentedServiceHost0 = System.getProperty("service1.host", "127.0.0.1");
        final String instrumentedServicePort0 = System.getProperty("service1.port", "32780");
        final String instrumentedServiceHost1 = System.getProperty("service2.host", "127.0.0.1");
        final String instrumentedServicePort1 = System.getProperty("service2.port", "32781");
        final String queryClientUrl = "http://" + swWebappHost + ":" + swWebappPort + "/graphql";
        queryClient = new SimpleQueryClient(queryClientUrl);
        instrumentedServiceUrl0 = "http://" + instrumentedServiceHost0 + ":" + instrumentedServicePort0;
        instrumentedServiceUrl1 = "http://" + instrumentedServiceHost1 + ":" + instrumentedServicePort1;
    }

    @Test
    @DirtiesContext
    public void verify() throws Exception {
        final LocalDateTime minutesAgo = LocalDateTime.now(ZoneOffset.UTC);

        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        final ResponseEntity<String> responseEntity0 = restTemplate.postForEntity(
            instrumentedServiceUrl0 + "/e2e/users",
            user,
            String.class
        );
        assertThat(responseEntity0.getStatusCode()).isEqualTo(HttpStatus.OK);

        final ResponseEntity<String> responseEntity1 = restTemplate.postForEntity(
            instrumentedServiceUrl1 + "/e2e/users",
            user,
            String.class
        );
        assertThat(responseEntity1.getStatusCode()).isEqualTo(HttpStatus.OK);

        Thread.sleep(10000);

        verifyTraces(minutesAgo);

        verifyServices(minutesAgo);

        verifyTopo(minutesAgo);
    }

    private void verifyTopo(LocalDateTime minutesAgo) throws Exception {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final TopoData topoData = queryClient.topo(
            new TopoQuery()
                .step("MINUTE")
                .start(minutesAgo.minusDays(1))
                .end(now)
        );

        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.topo.yml").getInputStream();

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
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.services.yml").getInputStream();

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
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.instances.yml").getInputStream();
        final InstancesMatcher instancesMatcher = new Yaml().loadAs(expectedInputStream, InstancesMatcher.class);
        instancesMatcher.verify(instances);
        return instances;
    }

    private Endpoints verifyServiceEndpoints(LocalDateTime minutesAgo, LocalDateTime now, Service service) throws Exception {
        Endpoints instances = queryClient.endpoints(
            new EndpointQuery().serviceId(service.getKey())
        );
        InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.endpoints.yml").getInputStream();
        final EndpointsMatcher endpointsMatcher = new Yaml().loadAs(expectedInputStream, EndpointsMatcher.class);
        endpointsMatcher.verify(instances);
        return instances;
    }

    private void verifyInstancesMetrics(Instances instances) throws Exception {
        final String[] instanceMetricsNames = new String[] {
            SERVICE_INSTANCE_RESP_TIME,
            SERVICE_INSTANCE_CPM,
            SERVICE_INSTANCE_SLA
        };
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : instanceMetricsNames) {
                LOGGER.info("verifying service instance response time: {}", instance);
                final Metrics instanceRespTime = queryClient.metrics(
                    new MetricsQuery()
                        .step("MINUTE")
                        .metricsName(metricsName)
                        .id(instance.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceRespTime);
                LOGGER.info("{}: {}", metricsName, instanceRespTime);
            }
        }
    }

    private void verifyEndpointsMetrics(Endpoints endpoints) throws Exception {
        final String[] endpointMetricsNames = {
            ENDPOINT_P99,
            ENDPOINT_P95,
            ENDPOINT_P90,
            ENDPOINT_P75,
            ENDPOINT_P50
        };
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            if (!endpoint.getLabel().equals("/e2e/users")) {
                continue;
            }
            for (String metricName : endpointMetricsNames) {
                LOGGER.info("verifying endpoint {}, metrics: {}", endpoint, metricName);
                final Metrics metrics = queryClient.metrics(
                    new MetricsQuery()
                        .step("MINUTE")
                        .metricsName(metricName)
                        .id(endpoint.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(metrics);
                LOGGER.info("metrics: {}", metrics);
            }
        }
    }

    private void verifyServiceMetrics(Service service) throws Exception {
        final String[] serviceMetrics = {
            SERVICE_P99,
            SERVICE_P95,
            SERVICE_P90,
            SERVICE_P75,
            SERVICE_P50
        };
        for (String metricName : serviceMetrics) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);
            final Metrics instanceRespTime = queryClient.metrics(
                new MetricsQuery()
                    .step("MINUTE")
                    .metricsName(metricName)
                    .id(service.getKey())
            );
            AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(instanceRespTime);
            LOGGER.info("instanceRespTime: {}", instanceRespTime);
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
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.ClusterVerificationITCase.traces.yml").getInputStream();

        final TracesMatcher tracesMatcher = new Yaml().loadAs(expectedInputStream, TracesMatcher.class);
        tracesMatcher.verify(traces);
    }
}
