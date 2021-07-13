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
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Events;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class EventQuery implements GraphQLQueryResolver {
    private EventQueryService queryService;

    private final ModuleManager moduleManager;

    public EventQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    EventQueryService queryService() {
        if (queryService != null) {
            return queryService;
        }

        queryService = moduleManager.find(CoreModule.NAME)
                                    .provider()
                                    .getService(EventQueryService.class);
        return queryService;
    }

    public Events queryEvents(final EventQueryCondition condition) throws Exception {
        return queryService().queryEvents(condition);
    }
}
