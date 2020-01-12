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

import com.google.common.io.Resources;
import org.apache.skywalking.e2e.metrics.Metrics;
import org.apache.skywalking.e2e.metrics.MetricsData;
import org.apache.skywalking.e2e.metrics.MetricsQuery;
import org.apache.skywalking.e2e.metrics.MultiMetricsData;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesData;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.topo.*;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TracesData;
import org.apache.skywalking.e2e.trace.TracesQuery;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author kezhenxu94
 */
public class SimpleQueryClient {
    protected final RestTemplate restTemplate = new RestTemplate();

    protected final String endpointUrl;

    public SimpleQueryClient(String host, String port) {
        this("http://" + host + ":" + port + "/graphql");
    }

    public SimpleQueryClient(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public List<Trace> traces(final TracesQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("traces.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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
            .replace("{queryOrder}", query.queryOrder());
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

    public List<Service> services(final ServicesQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("services.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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

    public TopoData topo(final TopoQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("topo.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
            .stream()
            .filter(it -> !it.startsWith("#"))
            .collect(Collectors.joining())
            .replace("{step}", query.step())
            .replace("{start}", query.start())
            .replace("{end}", query.end());
        final ResponseEntity<GQLResponse<TopoResponse>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<TopoResponse>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getTopo();
    }

    public ServiceInstanceTopoData serviceInstanceTopo(final ServiceInstanceTopoQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("instanceTopo.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
                .stream()
                .filter(it -> !it.startsWith("#"))
                .collect(Collectors.joining())
                .replace("{step}", query.step())
                .replace("{start}", query.start())
                .replace("{end}", query.end())
                .replace("{clientServiceId}", query.clientServiceId())
                .replace("{serverServiceId}", query.serverServiceId());
        final ResponseEntity<GQLResponse<ServiceInstanceTopoResponse>> responseEntity = restTemplate.exchange(
                new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
                new ParameterizedTypeReference<GQLResponse<ServiceInstanceTopoResponse>>() {
                }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getTopo();
    }

    public Metrics metrics(final MetricsQuery query) throws Exception {
        final URL queryFileUrl = Resources.getResource("metrics.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
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

}
