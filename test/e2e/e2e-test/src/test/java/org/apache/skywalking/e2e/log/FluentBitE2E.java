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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

@Slf4j
@SkyWalkingE2E
public class FluentBitE2E extends LogE2E {
    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/log/docker-compose.fluentbit.yml"
    })
    protected DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    protected HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "provider", port = 9090)
    protected HostAndPort providerHostPort;

    @Override
    @BeforeAll
    public void setUp() throws Exception {
        queryClient(swWebappHostPort);
        trafficController(providerHostPort, "/file/logs/trigger");
    }

    @Override
    @RetryableTest
    public void verifyService() throws Exception {
    }

    @Override
    @RetryableTest
    public void verifyLog4jLog() throws Exception {
        verify("fluentbit log4j fileLogger");
    }

    @Override
    @RetryableTest
    public void verifyLog4j2Log() throws Exception {
        verify("fluentbit log4j2 fileLogger");
    }

    @Override
    @RetryableTest
    public void verifyLogbackLog() throws Exception {
        verify("fluentbit logback fileLogger");
    }
}
