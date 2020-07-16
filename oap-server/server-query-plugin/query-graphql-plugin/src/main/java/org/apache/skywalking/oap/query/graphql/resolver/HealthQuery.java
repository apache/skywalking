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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.query.type.HealthStatus;
import org.apache.skywalking.oap.server.health.checker.module.HealthCheckerModule;
import org.apache.skywalking.oap.server.health.checker.provider.HealthQueryService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@RequiredArgsConstructor
public class HealthQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;

    private HealthQueryService service;

    private HealthQueryService getService() {
        return Optional.ofNullable(service)
            .orElseGet(() -> {
                service = moduleManager.find(HealthCheckerModule.NAME).provider().getService(HealthQueryService.class);
                return service;
            });
    }

    public HealthStatus checkHealth() {
        return getService().checkHealth();
    }
}
