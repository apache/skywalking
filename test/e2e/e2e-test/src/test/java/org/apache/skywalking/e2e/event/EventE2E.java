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

package org.apache.skywalking.e2e.event;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.ProfileClient;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static org.apache.skywalking.e2e.utils.Times.now;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class EventE2E extends SkyWalkingTestAdapter {
    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/event/docker-compose.yml",
        "docker/event/docker-compose.${SW_STORAGE}.yml",
    })
    protected DockerComposeContainer<?> compose;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "ui", port = 8080)
    protected HostAndPort swWebappHostPort;

    private ProfileClient graphql;

    @BeforeAll
    public void setUp() {
        graphql = new ProfileClient(swWebappHostPort.host(), swWebappHostPort.port());
    }

    @RetryableTest
    void events() throws Exception {
        final List<Event> events = graphql.events(new EventsQuery().start(startTime).end(now()).uuid("abcde"));

        LOGGER.info("events: {}", events);

        load("expected/event/events.yml").as(EventsMatcher.class).verifyLoosely(events);
    }

}
