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
import java.io.IOException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmTrend;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class AlarmQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private AlarmQueryService queryService;

    public AlarmQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AlarmQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(AlarmQueryService.class);
        }
        return queryService;
    }

    public AlarmTrend getAlarmTrend(final Duration duration) {
        return new AlarmTrend();
    }

    public Alarms getAlarm(final Duration duration, final Scope scope, final String keyword,
                           final Pagination paging) throws IOException {
        Integer scopeId = null;
        if (scope != null) {
            scopeId = scope.getScopeId();
        }

        return getQueryService().getAlarm(
            scopeId, keyword, paging, duration.getStartTimeBucketInSec(), duration.getEndTimeBucketInSec());
    }
}
