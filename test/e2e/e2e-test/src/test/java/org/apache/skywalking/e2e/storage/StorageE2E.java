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

package org.apache.skywalking.e2e.storage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.UIConfigurationManagementClient;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.dashboard.DashboardConfiguration;
import org.apache.skywalking.e2e.dashboard.DashboardConfigurations;
import org.apache.skywalking.e2e.dashboard.DashboardConfigurationsMatcher;
import org.apache.skywalking.e2e.dashboard.DashboardSetting;
import org.apache.skywalking.e2e.dashboard.TemplateChangeStatus;
import org.apache.skywalking.e2e.dashboard.TemplateType;
import org.apache.skywalking.e2e.metrics.AtLeastOneOfMetricsMatcher;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MetricsValueMatcher;
import org.apache.skywalking.e2e.metrics.ReadLabeledMetricsQuery;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
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
import org.apache.skywalking.e2e.topo.Call;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopology;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyMatcher;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyQuery;
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.topo.Topology;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesMatcher;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyPercentileMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_INSTANCE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_MULTIPLE_LINEAR_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SO11Y_LINER_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SO11Y_LABELED_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SkyWalkingE2E
public class StorageE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/storage/docker-compose.yml",
        "docker/storage/docker-compose.${SW_STORAGE}.yml"
    })
    protected DockerComposeContainer<?> compose;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    private HostAndPort serviceHostPort;

    private UIConfigurationManagementClient graphql;

    @BeforeAll
    void setUp() throws Exception {
        graphql = new UIConfigurationManagementClient(swWebappHostPort.host(), swWebappHostPort.port());

        trafficController(serviceHostPort, "/users");
    }

    @AfterAll
    public void tearDown() {
        trafficController.stop();
    }

    @RetryableTest
    void services() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/storage/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instances: {}", service);

            verifyServiceMetrics(service);

            final Instances instances = verifyServiceInstances(service);

            verifyInstancesMetrics(instances);

            final Endpoints endpoints = verifyServiceEndpoints(service);

            verifyEndpointsMetrics(endpoints);
        }
    }

    @RetryableTest
    void traces() throws Exception {
        final List<Trace> traces = graphql.traces(new TracesQuery().start(startTime).end(now()).orderByDuration());

        LOGGER.info("traces: {}", traces);

        load("expected/storage/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }

    @RetryableTest
    void topology() throws Exception {
        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/storage/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());
    }

    @RetryableTest
    void serviceInstanceTopo() throws Exception {
        final ServiceInstanceTopology topology = graphql.serviceInstanceTopo(
            new ServiceInstanceTopologyQuery().stepByMinute()
                                              .start(startTime.minusDays(1))
                                              .end(now())
                                              .clientServiceId("VXNlcg==.0")
                                              .serverServiceId("WW91cl9BcHBsaWNhdGlvbk5hbWU=.1"));

        LOGGER.info("instance topology: {}", topology);

        load("expected/storage/serviceInstanceTopo.yml").as(ServiceInstanceTopologyMatcher.class).verify(topology);

        verifyServiceInstanceRelationMetrics(topology.getCalls());
    }

    @Test
    void addUITemplate() throws Exception {
        assertTrue(
            graphql.addTemplate(
                emptySetting("test-ui-config-1").type(TemplateType.DASHBOARD)
            ).isStatus()
        );
        TimeUnit.SECONDS.sleep(2L);

        verifyTemplates("expected/storage/dashboardConfiguration.yml");
    }

    @Test
    void changeTemplate() throws Exception {
        final String name = "test-ui-config-2";
        assertTrue(
            graphql.addTemplate(
                emptySetting(name).type(TemplateType.DASHBOARD)
            ).isStatus()
        );
        TimeUnit.SECONDS.sleep(2L);

        TemplateChangeStatus templateChangeStatus = graphql.changeTemplate(
            emptySetting(name).type(TemplateType.TOPOLOGY_SERVICE)
        );
        LOGGER.info("change UITemplate = {}", templateChangeStatus);
        assertTrue(templateChangeStatus.isStatus());

        TimeUnit.SECONDS.sleep(2L);
        verifyTemplates("expected/storage/dashboardConfiguration-change.yml");
    }

    @Test
    void disableTemplate() throws Exception {
        final String name = "test-ui-config-3";
        assertTrue(
            graphql.addTemplate(
                emptySetting(name).type(TemplateType.DASHBOARD)
            ).isStatus()
        );
        TimeUnit.SECONDS.sleep(2L);

        TemplateChangeStatus templateChangeStatus = graphql.disableTemplate(name);
        LOGGER.info("disable template = {}", templateChangeStatus);
        assertTrue(templateChangeStatus.isStatus());

        TimeUnit.SECONDS.sleep(2L);
        verifyTemplates("expected/storage/dashboardConfiguration-disable.yml");
    }

    @RetryableTest
    void so11y() throws Exception {
        List<Service> services = graphql.services(new ServicesQuery().start(startTime).end(now()));

        services = services.stream().filter(s -> s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);
        load("expected/simple/so11y-services.yml").as(ServicesMatcher.class).verify(services);
        for (final Service service : services) {
            final Instances instances = graphql.instances(
                new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
            );

            LOGGER.info("instances: {}", instances);

            load("expected/simple/so11y-instances.yml").as(InstancesMatcher.class).verify(instances);
            for (Instance instance : instances.getInstances()) {
                for (String metricsName : ALL_SO11Y_LINER_METRICS) {
                    LOGGER.info("verifying service instance response time: {}", instance);
                    final ReadMetrics instanceMetrics = graphql.readMetrics(
                        new ReadMetricsQuery().stepByMinute().metricsName(metricsName)
                            .serviceName(service.getLabel()).instanceName(instance.getLabel())
                    );
    
                    LOGGER.info("{}: {}", metricsName, instanceMetrics);
                    final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    instanceRespTimeMatcher.verify(instanceMetrics.getValues());
                }
                for (String metricsName : ALL_SO11Y_LABELED_METRICS) {
                    LOGGER.info("verifying service instance response time: {}", instance);
                    final List<ReadMetrics> instanceMetrics = graphql.readLabeledMetrics(
                        new ReadLabeledMetricsQuery().stepByMinute().metricsName(metricsName)
                            .serviceName(service.getLabel()).instanceName(instance.getLabel())
                            .labels(Arrays.asList("50", "70", "90", "99"))
                    );
    
                    LOGGER.info("{}: {}", metricsName, instanceMetrics);
                    Metrics allValues = new Metrics();
                    for (ReadMetrics readMetrics : instanceMetrics) {
                        allValues.getValues().addAll(readMetrics.getValues().getValues());
                    }
                    final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                    final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                    greaterThanZero.setValue("gt 0");
                    instanceRespTimeMatcher.setValue(greaterThanZero);
                    instanceRespTimeMatcher.verify(allValues);
                }
            }
        }
    }

    private Instances verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now())
        );

        LOGGER.info("instances: {}", instances);

        load("expected/storage/instances.yml").as(InstancesMatcher.class).verify(instances);

        return instances;
    }

    private Endpoints verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));

        LOGGER.info("endpoints: {}", endpoints);

        load("expected/storage/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);

        return endpoints;
    }

    private void verifyInstancesMetrics(final Instances instances) throws Exception {
        for (Instance instance : instances.getInstances()) {
            for (String metricsName : ALL_INSTANCE_METRICS) {
                LOGGER.info("verifying service instance response time: {}", instance);
                final Metrics instanceMetrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricsName).id(instance.getKey())
                );

                LOGGER.info("instance metrics: {}", instanceMetrics);

                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(instanceMetrics);
                LOGGER.info("{}: {}", metricsName, instanceMetrics);
            }
        }
    }

    private void verifyEndpointsMetrics(final Endpoints endpoints) throws Exception {
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            if (!endpoint.getLabel().equals("/users")) {
                continue;
            }
            for (final String metricName : ALL_ENDPOINT_METRICS) {
                LOGGER.info("verifying endpoint {}: {}", endpoint, metricName);

                final Metrics metrics = graphql.metrics(
                    new MetricsQuery().stepByMinute().metricsName(metricName).id(endpoint.getKey())
                );

                LOGGER.info("metrics: {}", metrics);

                final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
                final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
                greaterThanZero.setValue("gt 0");
                instanceRespTimeMatcher.setValue(greaterThanZero);
                instanceRespTimeMatcher.verify(metrics);

                LOGGER.info("{}: {}", metricName, metrics);
            }

            for (String metricName : ALL_ENDPOINT_MULTIPLE_LINEAR_METRICS) {
                verifyPercentileMetrics(graphql, metricName, endpoint.getKey(), startTime);
            }
        }
    }

    private void verifyServiceMetrics(final Service service) throws Exception {
        for (String metricName : ALL_SERVICE_METRICS) {
            LOGGER.info("verifying service {}, metrics: {}", service, metricName);
            final Metrics serviceMetrics = graphql.metrics(
                new MetricsQuery().stepByMinute().metricsName(metricName).id(service.getKey())
            );
            LOGGER.info("serviceMetrics: {}", serviceMetrics);
            final AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            final MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            instanceRespTimeMatcher.verify(serviceMetrics);
            LOGGER.info("{}: {}", metricName, serviceMetrics);
        }

        for (String metricName : ALL_SERVICE_MULTIPLE_LINEAR_METRICS) {
            verifyPercentileMetrics(graphql, metricName, service.getKey(), startTime);
        }
    }

    private void verifyServiceInstanceRelationMetrics(final List<Call> calls) throws Exception {
        verifyRelationMetrics(
            calls, ALL_SERVICE_INSTANCE_RELATION_CLIENT_METRICS,
            ALL_SERVICE_INSTANCE_RELATION_SERVER_METRICS
        );
    }

    private void verifyServiceRelationMetrics(final List<Call> calls) throws Exception {
        verifyRelationMetrics(calls, ALL_SERVICE_RELATION_CLIENT_METRICS, ALL_SERVICE_RELATION_SERVER_METRICS);
    }

    private void verifyRelationMetrics(final List<Call> calls,
                                       final String[] relationClientMetrics,
                                       final String[] relationServerMetrics) throws Exception {
        for (Call call : calls) {
            for (String detectPoint : call.getDetectPoints()) {
                switch (detectPoint) {
                    case "CLIENT": {
                        for (String metricName : relationClientMetrics) {
                            verifyMetrics(graphql, metricName, call.getId(), startTime);
                        }
                        break;
                    }
                    case "SERVER": {
                        for (String metricName : relationServerMetrics) {
                            verifyMetrics(graphql, metricName, call.getId(), startTime);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void verifyTemplates(String file) throws IOException {
        List<DashboardConfiguration> configurations = graphql.getAllTemplates(Boolean.TRUE);
        LOGGER.info("get all templates = {}", configurations);
        DashboardConfigurations dashboardConfigurations = new DashboardConfigurations();
        dashboardConfigurations.setConfigurations(configurations);
        load(file).as(DashboardConfigurationsMatcher.class).verify(dashboardConfigurations);
    }

    private DashboardSetting emptySetting(final String name) {
        return new DashboardSetting()
            .name(name)
            .active(true)
            .configuration("{}");
    }

}
