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

package org.apache.skywalking.e2e.browser;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
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
import org.apache.skywalking.e2e.topo.TopoMatcher;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.topo.Topology;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesMatcher;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_ERROR_SUM;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_DOM_ANALYSIS_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_DOM_READY_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_DOM_READY_PERCENTILE;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_ERROR_SUM;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_JS_ERROR_SUM;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_LOAD_PAGE_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_LOAD_PAGE_PERCENTILE;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_PV;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_REDIRECT_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_RES_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PAGE_TTL_AVG;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_PV;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_SINGLE_VERSION_ERROR_SUM;
import static org.apache.skywalking.e2e.metrics.BrowserMetricsQuery.BROWSER_APP_SINGLE_VERSION_PV;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsMatcher.verifyPercentileMetrics;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_CLIENT_METRICS;
import static org.apache.skywalking.e2e.metrics.MetricsQuery.ALL_SERVICE_RELATION_SERVER_METRICS;
import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class BrowserWithClientJSE2E extends SkyWalkingTestAdapter {

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/browser/docker-compose.h2.client-js.yml"
    })
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    private HostAndPort swWebappHostPort;

    @BeforeAll
    public void setUp() {
        queryClient(swWebappHostPort);
    }

    @RetryableTest
    public void verifyBrowserData() throws Exception {
        final List<Service> services = graphql.browserServices(new ServicesQuery().start(startTime).end(now()));
        LOGGER.info("services: {}", services);

        load("expected/browser-with-client-js/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service version: {}", service);
            // browser metrics
            verifyBrowserMetrics(service);
            // browser single version
            verifyBrowserSingleVersion(service);
            // browser page path
            verifyBrowserPagePath(service);
        }
    }

    @RetryableTest
    public void errorLogs() throws Exception {
        List<BrowserErrorLog> logs = graphql.browserErrorLogs(new BrowserErrorLogQuery().start(startTime).end(now()));

        LOGGER.info("errorLogs: {}", logs);

        load("expected/browser-with-client-js/error-log.yml").as(BrowserErrorLogsMatcher.class).verifyLoosely(logs);
    }

    @RetryableTest
    void traces() throws Exception {
        final List<Trace> traces = graphql.traces(new TracesQuery().start(startTime).end(now()).orderByStartTime());

        LOGGER.info("traces: {}", traces);

        load("expected/browser-with-client-js/traces.yml").as(TracesMatcher.class).verifyLoosely(traces);
    }

    @RetryableTest
    void topology() throws Exception {
        final Topology topology = graphql.topo(new TopoQuery().stepByMinute().start(startTime.minusDays(1)).end(now()));

        LOGGER.info("topology: {}", topology);

        load("expected/browser-with-client-js/topo.yml").as(TopoMatcher.class).verify(topology);

        verifyServiceRelationMetrics(topology.getCalls());
    }

    private static final String[] BROWSER_METRICS = {
        BROWSER_APP_PV,
        BROWSER_APP_ERROR_SUM
    };

    private void verifyBrowserMetrics(final Service service) throws Exception {
        for (String metricName : BROWSER_METRICS) {
            verifyMetrics(graphql, metricName, service.getKey(), startTime);
        }
    }

    private void verifyBrowserSingleVersion(final Service service) throws Exception {
        Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(now()));
        LOGGER.info("instances: {}", instances);
        load("expected/browser-with-client-js/version.yml").as(InstancesMatcher.class).verify(instances);
        // service version metrics
        for (Instance instance : instances.getInstances()) {
            verifyBrowserSingleVersionMetrics(instance);
        }
    }

    public static final String[] BROWSER_SINGLE_VERSION_METRICS = {
        BROWSER_APP_SINGLE_VERSION_PV,
        BROWSER_APP_SINGLE_VERSION_ERROR_SUM
    };

    private void verifyBrowserSingleVersionMetrics(Instance instance) throws Exception {
        for (String metricName : BROWSER_SINGLE_VERSION_METRICS) {
            verifyMetrics(graphql, metricName, instance.getKey(), startTime);
        }
    }

    private void verifyBrowserPagePath(final Service service) throws Exception {
        Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(String.valueOf(service.getKey())));
        LOGGER.info("endpoints: {}", endpoints);
        load("expected/browser-with-client-js/page-path.yml").as(EndpointsMatcher.class).verify(endpoints);
        // service page metrics
        for (Endpoint endpoint : endpoints.getEndpoints()) {
            verifyBrowserPagePathMetrics(endpoint);
        }
    }

    public static final String[] BROWSER_PAGE_METRICS = {
        BROWSER_APP_PAGE_PV,
        BROWSER_APP_PAGE_ERROR_SUM,
        BROWSER_APP_PAGE_JS_ERROR_SUM,
        BROWSER_APP_PAGE_REDIRECT_AVG,
        BROWSER_APP_PAGE_DOM_ANALYSIS_AVG,
        BROWSER_APP_PAGE_DOM_READY_AVG,
        BROWSER_APP_PAGE_LOAD_PAGE_AVG,
        BROWSER_APP_PAGE_RES_AVG,
        BROWSER_APP_PAGE_TTL_AVG,
        };

    public static final String[] BROWSER_PAGE_MULTIPLE_LINEAR_METRICS = {
        BROWSER_APP_PAGE_DOM_READY_PERCENTILE,
        BROWSER_APP_PAGE_LOAD_PAGE_PERCENTILE,
        };

    private void verifyBrowserPagePathMetrics(Endpoint endpoint) throws Exception {
        for (String metricName : BROWSER_PAGE_METRICS) {
            verifyMetrics(graphql, metricName, endpoint.getKey(), startTime);
        }

        for (String metricName : BROWSER_PAGE_MULTIPLE_LINEAR_METRICS) {
            verifyPercentileMetrics(graphql, metricName, endpoint.getKey(), startTime);
        }
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
}
