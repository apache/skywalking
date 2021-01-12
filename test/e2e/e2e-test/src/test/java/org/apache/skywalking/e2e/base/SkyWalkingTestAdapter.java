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

package org.apache.skywalking.e2e.base;

import com.google.common.collect.ImmutableMap;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.skywalking.e2e.SimpleQueryClient;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.utils.Times;
import org.springframework.web.client.RestTemplate;

public abstract class SkyWalkingTestAdapter {
    protected final RestTemplate restTemplate = new RestTemplate();
    protected final LocalDateTime startTime = Times.now();

    protected Map<String, String> trafficData = ImmutableMap.of("name", "SkyWalking");

    protected SimpleQueryClient graphql;
    protected TrafficController trafficController;

    protected void queryClient(final HostAndPort swWebappHostPort) {
        graphql = new SimpleQueryClient(swWebappHostPort.host(), swWebappHostPort.port());
    }

    protected void trafficController(final HostAndPort serviceHostPort,
                                     final String path) throws Exception {
        final URL url = new URL("http", serviceHostPort.host(), serviceHostPort.port(), path);

        trafficController =
            TrafficController.builder()
                             .sender(() -> restTemplate.postForEntity(url.toURI(), trafficData, String.class))
                             .build()
                             .start();

    }
}
