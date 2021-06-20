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

package org.apache.skywalking.e2e.log;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.instance.Instance;
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
public class LogE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/log/docker-compose.${SW_STORAGE}.yml"
    })
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    protected HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    protected HostAndPort providerHostPort;

    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);
        trafficController(providerHostPort, "/logs/trigger");
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
    public void verifyLog4jLog() throws Exception {
        verify("log4j message");
    }

    @RetryableTest
    public void verifyLog4j2Log() throws Exception {
        verify("log4j2 message");
    }

    @RetryableTest
    public void verifyLogbackLog() throws Exception {
        verify("logback message");
    }

    protected void verify(String keyword) throws Exception {
        LogsQuery logsQuery = new LogsQuery().serviceId("WW91cl9BcHBsaWNhdGlvbk5hbWU=.1")
                                             .start(startTime)
                                             .end(Times.now())
                                             .addTag("level", "INFO");
        if (graphql.supportQueryLogsByKeywords()) {
            logsQuery.keywordsOfContent(keyword);
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

        verifyInstanceMetrics(service, instances);
    }

    private void verifyInstanceMetrics(final Service service, final Instances instances) throws Exception {
        for (Instance instance : instances.getInstances()) {
            final String metricsName = "log_count_info";
            LOGGER.info("verifying service instance response time: {}", instance);
            final ReadMetrics instanceMetrics = graphql.readMetrics(
                new ReadMetricsQuery().stepByMinute().metricsName(metricsName)
                                      .serviceName(service.getLabel()).instanceName(instance.getLabel())
            );

            LOGGER.info("{}: {}", metricsName, instanceMetrics);
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanOne = new MetricsValueMatcher();
            greaterThanOne.setValue("gt 1");
            instanceRespTimeMatcher.setValue(greaterThanOne);
            instanceRespTimeMatcher.verify(instanceMetrics.getValues());
        }
    }

}
