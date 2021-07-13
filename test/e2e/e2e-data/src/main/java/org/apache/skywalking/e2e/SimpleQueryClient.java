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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.alarm.AlarmQuery;
import org.apache.skywalking.e2e.alarm.GetAlarm;
import org.apache.skywalking.e2e.alarm.GetAlarmData;
import org.apache.skywalking.e2e.browser.BrowserErrorLog;
import org.apache.skywalking.e2e.browser.BrowserErrorLogQuery;
import org.apache.skywalking.e2e.browser.BrowserErrorLogsData;
import org.apache.skywalking.e2e.event.Event;
import org.apache.skywalking.e2e.event.EventData;
import org.apache.skywalking.e2e.event.EventsQuery;
import org.apache.skywalking.e2e.log.Log;
import org.apache.skywalking.e2e.log.LogData;
import org.apache.skywalking.e2e.log.LogsQuery;
import org.apache.skywalking.e2e.log.SupportQueryLogsByKeywords;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsData;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MultiMetricsData;
import org.apache.skywalking.e2e.metrics.ReadLabeledMetricsData;
import org.apache.skywalking.e2e.metrics.ReadLabeledMetricsQuery;
import org.apache.skywalking.e2e.metrics.ReadMetrics;
import org.apache.skywalking.e2e.metrics.ReadMetricsData;
import org.apache.skywalking.e2e.metrics.ReadMetricsQuery;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesData;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopology;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyQuery;
import org.apache.skywalking.e2e.topo.ServiceInstanceTopologyResponse;
import org.apache.skywalking.e2e.topo.TopoQuery;
import org.apache.skywalking.e2e.topo.Topology;
import org.apache.skywalking.e2e.topo.TopologyResponse;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesData;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SuppressWarnings("UnstableApiUsage")
@Slf4j
public class SimpleQueryClient {
    protected final RestTemplate restTemplate = new RestTemplate();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected final String endpointUrl;

    public SimpleQueryClient(String host, int port) {
        this("http://" + host + ":" + port + "/graphql");
    }

    public SimpleQueryClient(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public List<Trace> traces(final TracesQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("traces.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{step}", query.step())
                                            .replace("{traceState}", query.traceState())
                                            .replace("{pageNum}", query.pageNum())
                                            .replace("{pageSize}", query.pageSize())
                                            .replace("{needTotal}", query.needTotal())
                                            .replace("{queryOrder}", query.queryOrder())
                                            .replace("{tags}", objectMapper.writeValueAsString(query.tags()));
        final ResponseEntity<GQLResponse<TracesData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<TracesData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getTraces().getData();
    }

    public List<BrowserErrorLog> browserErrorLogs(final BrowserErrorLogQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("browser-error-logs.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{step}", query.step())
                                            .replace("{pageNum}", query.pageNum())
                                            .replace("{pageSize}", query.pageSize())
                                            .replace("{needTotal}", query.needTotal());
        final ResponseEntity<GQLResponse<BrowserErrorLogsData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<BrowserErrorLogsData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody().getData().getLogs().getData());
    }

    public List<Service> services(final ServicesQuery query) throws Exception {
        return services(query, "services.gql");
    }

    public List<Service> browserServices(final ServicesQuery query) throws Exception {
        return services(query, "browser-services.gql");
    }

    private List<Service> services(final ServicesQuery query, String gql) throws Exception {
        final URL queryFileUrl = Resources.getResource(gql);
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{step}", query.step());
        final ResponseEntity<GQLResponse<ServicesData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<ServicesData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getServices();
    }

    public Instances instances(final InstancesQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("instances.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{serviceId}", query.serviceId())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{step}", query.step());
        final ResponseEntity<GQLResponse<Instances>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<Instances>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData();
    }

    public Endpoints endpoints(final EndpointQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("endpoints.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{serviceId}", query.serviceId());
        final ResponseEntity<GQLResponse<Endpoints>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<Endpoints>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData();
    }

    public Topology topo(final TopoQuery query) throws Exception {
        LOGGER.info("topo {}", query);

        final URL queryFileUrl = Resources.getResource("topo.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end());

        LOGGER.info("query string {}", queryString);

        try {
            final ResponseEntity<GQLResponse<TopologyResponse>> responseEntity = restTemplate.exchange(
                new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
                new ParameterizedTypeReference<GQLResponse<TopologyResponse>>() {
                }
            );

            LOGGER.info("response {}", responseEntity);

            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
            }

            return Objects.requireNonNull(responseEntity.getBody()).getData().getTopo();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return new Topology();
    }

    public ServiceInstanceTopology serviceInstanceTopo(final ServiceInstanceTopologyQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("instanceTopo.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{clientServiceId}", query.clientServiceId())
                                            .replace("{serverServiceId}", query.serverServiceId());
        final ResponseEntity<GQLResponse<ServiceInstanceTopologyResponse>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<ServiceInstanceTopologyResponse>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getTopo();
    }

    public Metrics metrics(final MetricsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("metrics.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{metricsName}", query.metricsName())
                                            .replace("{id}", query.id());
        final ResponseEntity<GQLResponse<MetricsData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<MetricsData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getMetrics();
    }

    public List<Metrics> multipleLinearMetrics(final MetricsQuery query, String numOfLinear) throws Exception {
        final URL queryFileUrl = Resources.getResource("metrics-multiLines.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{metricsName}", query.metricsName())
                                            .replace("{id}", query.id())
                                            .replace("{numOfLinear}", numOfLinear);
        final ResponseEntity<GQLResponse<MultiMetricsData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<MultiMetricsData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getMetrics();
    }

    public ReadMetrics readMetrics(final ReadMetricsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("read-metrics.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{metricsName}", query.metricsName())
                                            .replace("{serviceName}", query.serviceName())
                                            .replace("{instanceName}", query.instanceName())
                                            .replace("{scope}", query.scope());
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<ReadMetricsData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<ReadMetricsData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getReadMetricsValues();
    }

    public List<ReadMetrics> readLabeledMetrics(final ReadLabeledMetricsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("read-labeled-metrics.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{metricsName}", query.metricsName())
                                            .replace("{serviceName}", query.serviceName())
                                            .replace("{instanceName}", query.instanceName())
                                            .replace("{scope}", query.scope())
                                            .replace("{labels}", query.labels().stream()
                                                    .map(s -> "\"" + s + "\"").collect(Collectors.joining(",")));
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<ReadLabeledMetricsData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<ReadLabeledMetricsData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getReadLabeledMetricsValues();
    }

    public GetAlarm readAlarms(final AlarmQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("read-alarms.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{step}", query.step())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{pageSize}", "20")
                                            .replace("{needTotal}", "true")
                                            .replace("{tags}", objectMapper.writeValueAsString(query.tags()));
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<GetAlarmData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<GetAlarmData>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }
        LOGGER.info("Result: {}", responseEntity.getBody());
        return Objects.requireNonNull(responseEntity.getBody()).getData().getGetAlarm();
    }

    public List<Log> logs(final LogsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("logs.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream().filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{serviceId}", query.serviceId())
                                            .replace("{endpointId}", query.endpointId())
                                            .replace("{endpointName}", query.endpointName())
                                            .replace("{start}", query.start())
                                            .replace("{end}", query.end())
                                            .replace("{step}", query.step())
                                            .replace("{pageNum}", query.pageNum())
                                            .replace("{pageSize}", query.pageSize())
                                            .replace("{needTotal}", query.needTotal())
                                            .replace("{keywordsOfContent}", query.keywordsOfContent())
                                            .replace(
                                                "{excludingKeywordsOfContent}", query.excludingKeywordsOfContent())
                                            .replace("{tags}", objectMapper.writeValueAsString(query.tags()));
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<LogData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<LogData>>() {
            }
        );
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }
        return Objects.requireNonNull(responseEntity.getBody()).getData().getLogs().getData();
    }

    public boolean supportQueryLogsByKeywords() throws Exception {
        final URL queryFileUrl = Resources.getResource("support-query-logs-by-keywords.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining());
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<SupportQueryLogsByKeywords>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<SupportQueryLogsByKeywords>>() {
            }
        );
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }
        return Objects.requireNonNull(responseEntity.getBody().getData().isSupport());
    }

    public List<Event> events(final EventsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("events.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream().filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{uuid}", query.uuid())
                                            .replace("{pageNum}", query.pageNum())
                                            .replace("{pageSize}", query.pageSize())
                                            .replace("{needTotal}", query.needTotal());
        LOGGER.info("Query: {}", queryString);
        final ResponseEntity<GQLResponse<EventData>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<EventData>>() {
            }
        );
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }
        return Objects.requireNonNull(responseEntity.getBody()).getData().getEvents().getData();
    }
}
