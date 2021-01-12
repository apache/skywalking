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

import org.apache.skywalking.e2e.annotation.ContainerHost;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.ContainerPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link Extension} that supports the {@link DockerCompose @DockerCompose}, {@link ContainerHost @ContainerHost} and
 * {@link ContainerPort @ContainerPort}, {@link ContainerHostAndPort @ContainerHostAndPort} annotations.
 *
 * <pre>{@code
 * @ExtendWith(SkyWalkingExtension.class)
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)
 * public class SomeTest {
 *     @DockerCompose("docker-compose.yml")
 *     private DockerComposeContainer justForSideEffects;
 *
 *     @ContainerHostAndPort(name = "service-name1-in-docker-compose.yml", port = 8080)
 *     private HostAndPort someService1HostPort;
 *
 *     @ContainerHostAndPort(name = "service-name2-in-docker-compose.yml", port = 9090)
 *     private HostAndPort someService2HostPort;
 * }
 * }</pre>
 */
public final class SkyWalkingExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        SkyWalkingAnnotations.init(context.getRequiredTestInstance());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        SkyWalkingAnnotations.destroy(context.getRequiredTestInstance());
    }
}
