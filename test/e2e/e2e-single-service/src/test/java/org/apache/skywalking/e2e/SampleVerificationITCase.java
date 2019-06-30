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

import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.instance.Instance;
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

import static org.apache.skywalking.e2e.metrics.MetricsQuery.SERVICE_INSTANCE_RESP_TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleVerificationITCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleVerificationITCase.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private SimpleQueryClient queryClient;
    private String instrumentedServiceUrl;

    @Before
    public void setUp() {
        final String swWebappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String swWebappPort = System.getProperty("sw.webapp.port", "32781");
        final String instrumentedServiceHost0 = System.getProperty("client.host", "127.0.0.1");
        final String instrumentedServicePort0 = System.getProperty("client.port", "32780");
        final String queryClientUrl = "http://" + swWebappHost + ":" + swWebappPort + "/graphql";
        queryClient = new SimpleQueryClient(queryClientUrl);
        instrumentedServiceUrl = "http://" + instrumentedServiceHost0 + ":" + instrumentedServicePort0;
    }

    @Test
    @DirtiesContext
    public void verify() throws Exception {
        final LocalDateTime minutesAgo = LocalDateTime.now(ZoneOffset.UTC);

        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        final ResponseEntity<String> responseEntity = restTemplate.postForEntity(
            instrumentedServiceUrl + "/e2e/users",
            user,
            String.class
        );
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        Thread.sleep(5000);

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
            List<Instance> instances = queryClient.instances(
                new InstancesQuery()
                    .serviceId(service.getKey())
                    .start(minutesAgo)
                    .end(now)
            );
            expectedInputStream =
                new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.instances.yml").getInputStream();
            final InstancesMatcher instancesMatcher = new Yaml().loadAs(expectedInputStream, InstancesMatcher.class);
            instancesMatcher.verify(instances);

            for (Instance instance : instances) {
                LOGGER.info("verifying service instance response time: {}", instance);
                final Metrics instanceRespTime = queryClient.metrics(
                    new MetricsQuery()
                        .step("MINUTE")
                        .metricsName(SERVICE_INSTANCE_RESP_TIME)
                        .id(instance.getKey())
                );
                AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceRespTime);
                LOGGER.info("instanceRespTime: {}", instanceRespTime);
            }
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
        tracesMatcher.verify(traces);
    }
}
