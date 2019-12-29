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

package org.apache.skywalking.e2e.profile;

import com.google.common.io.Resources;
import org.apache.skywalking.e2e.GQLResponse;
import org.apache.skywalking.e2e.SimpleQueryClient;
import org.apache.skywalking.e2e.profile.threadmonitor.creation.ThreadMonitorTaskCreationRequest;
import org.apache.skywalking.e2e.profile.threadmonitor.creation.ThreadMonitorTaskCreationResult;
import org.apache.skywalking.e2e.profile.threadmonitor.creation.ThreadMonitorTaskCreationResultWrapper;
import org.apache.skywalking.e2e.profile.threadmonitor.query.ThreadMonitorTaskQuery;
import org.apache.skywalking.e2e.profile.threadmonitor.query.ThreadMonitorTasks;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

/**
 * Profile client, use profile.graphqls, base on {@link SimpleQueryClient}
 *
 * @author MrPro
 */
public class ProfileClient extends SimpleQueryClient {
    public ProfileClient(String host, String port) {
        super(host, port);
    }

    public ThreadMonitorTaskCreationResult createThreadMonitorTask(final ThreadMonitorTaskCreationRequest creationRequest) throws Exception {
        final URL queryFileUrl = Resources.getResource("threadMonitorTaskCreation.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
                .stream()
                .filter(it -> !it.startsWith("#"))
                .collect(Collectors.joining())
                .replace("{serviceId}", String.valueOf(creationRequest.getServiceId()))
                .replace("{endpointName}", creationRequest.getEndpointName())
                .replace("{duration}", String.valueOf(creationRequest.getDuration()))
                .replace("{startTime}", String.valueOf(creationRequest.getStartTime()))
                .replace("{durationUnit}", creationRequest.getDurationUnit())
                .replace("{minDurationThreshold}", String.valueOf(creationRequest.getMinDurationThreshold()))
                .replace("{dumpPeriod}", String.valueOf(creationRequest.getDumpPeriod()));
        final ResponseEntity<GQLResponse<ThreadMonitorTaskCreationResultWrapper>> responseEntity = restTemplate.exchange(
                new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
                new ParameterizedTypeReference<GQLResponse<ThreadMonitorTaskCreationResultWrapper>>() {
                }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return responseEntity.getBody().getData().getCreationResult();
    }

    public ThreadMonitorTasks getThreadMonitorTaskList(final ThreadMonitorTaskQuery query) throws IOException {
        final URL queryFileUrl = Resources.getResource("getThreadMonitorTaskList.gql");
        final String queryString = Resources.readLines(queryFileUrl, Charset.forName("UTF8"))
                .stream()
                .filter(it -> !it.startsWith("#"))
                .collect(Collectors.joining())
                .replace("{start}", query.start())
                .replace("{end}", query.end())
                .replace("{step}", query.step())
                .replace("{serviceId}", String.valueOf(query.serviceId()))
                .replace("{endpointName}", query.endpointName());
        final ResponseEntity<GQLResponse<ThreadMonitorTasks>> responseEntity = restTemplate.exchange(
                new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
                new ParameterizedTypeReference<GQLResponse<ThreadMonitorTasks>>() {
                }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return responseEntity.getBody().getData();
    }


}
