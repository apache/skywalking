/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.webapp;

import static java.util.stream.Collectors.toList;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroup;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import lombok.SneakyThrows;

public final class OapProxyService extends AbstractHttpService {
    private final WebClient loadBalancingClient;

    public OapProxyService(String[] oapServices) throws Exception {
        final List<Endpoint> endpoints =
            Stream
                .of(oapServices)
                .map(URI::create)
                .map(URI::getAuthority)
                .map(Endpoint::parse)
                .collect(toList());
        loadBalancingClient = newLoadBalancingClient(
            EndpointGroup.of(
                EndpointSelectionStrategy.roundRobin(),
                endpoints));
    }

    @SneakyThrows
    private static WebClient newLoadBalancingClient(EndpointGroup oapGroup) {
        final HealthCheckedEndpointGroup healthCheckedGroup =
            HealthCheckedEndpointGroup
                .builder(oapGroup, "/internal/l7check")
                .protocol(SessionProtocol.HTTP)
                .retryInterval(Duration.ofSeconds(10))
                .build();

        // Wait until the initial health check is finished.
        healthCheckedGroup.whenReady().get();

        return WebClient
            .builder(SessionProtocol.HTTP, oapGroup)
            .decorator(LoggingClient.newDecorator())
            .build();
    }

    @Override
    protected HttpResponse doPost(ServiceRequestContext ctx, HttpRequest req) throws Exception {
        return loadBalancingClient.execute(req);
    }
}
