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
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.e2e.dashboard.DashboardConfiguration;
import org.apache.skywalking.e2e.dashboard.DashboardConfigurationListWrapper;
import org.apache.skywalking.e2e.dashboard.DashboardSetting;
import org.apache.skywalking.e2e.dashboard.TemplateChangeStatus;
import org.apache.skywalking.e2e.dashboard.TemplateChangeStatusWrapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

public class UIConfigurationManagementClient extends SimpleQueryClient {

    public UIConfigurationManagementClient(final String endpointUrl) {
        super(endpointUrl);
    }

    public UIConfigurationManagementClient(final String host, final int port) {
        super(host, port);
    }

    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        final URL queryFileUrl = Resources.getResource("ui-addTemplate.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{name}", setting.name())
                                            .replace("{type}", String.valueOf(setting.type()))
                                            .replace("{configuration}", setting.configuration())
                                            .replace("{active}", String.valueOf(setting.active()));

        final ResponseEntity<GQLResponse<TemplateChangeStatusWrapper>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<TemplateChangeStatusWrapper>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getChangeStatusResult();
    }

    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        final URL queryFileUrl = Resources.getResource("ui-getTemplates.gql");
        final String queryString =
            Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                     .stream()
                     .filter(it -> !it.startsWith("#"))
                     .collect(Collectors.joining())
                     .replace("{includingDisabled}", String.valueOf(includingDisabled));

        final ResponseEntity<GQLResponse<DashboardConfigurationListWrapper>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<DashboardConfigurationListWrapper>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getConfigurations();
    }

    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {

        final URL queryFileUrl = Resources.getResource("ui-changeTemplate.gql");
        final String queryString = Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                                            .stream()
                                            .filter(it -> !it.startsWith("#"))
                                            .collect(Collectors.joining())
                                            .replace("{name}", setting.name())
                                            .replace("{type}", String.valueOf(setting.type()))
                                            .replace("{configuration}", setting.configuration())
                                            .replace("{active}", String.valueOf(setting.active()));

        final ResponseEntity<GQLResponse<TemplateChangeStatusWrapper>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<TemplateChangeStatusWrapper>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getChangeStatusResult();
    }

    public TemplateChangeStatus disableTemplate(String name) throws IOException {

        final URL queryFileUrl = Resources.getResource("ui-disableTemplate.gql");
        final String queryString =
            Resources.readLines(queryFileUrl, StandardCharsets.UTF_8)
                     .stream()
                     .filter(it -> !it.startsWith("#"))
                     .collect(Collectors.joining())
                     .replace("{name}", name);

        final ResponseEntity<GQLResponse<TemplateChangeStatusWrapper>> responseEntity = restTemplate.exchange(
            new RequestEntity<>(queryString, HttpMethod.POST, URI.create(endpointUrl)),
            new ParameterizedTypeReference<GQLResponse<TemplateChangeStatusWrapper>>() {
            }
        );

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Response status != 200, actual: " + responseEntity.getStatusCode());
        }

        return Objects.requireNonNull(responseEntity.getBody()).getData().getChangeStatusResult();
    }

}
