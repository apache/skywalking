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
import com.google.common.base.Strings;
import java.io.IOException;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.nonNull;

public class TraceQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TraceQueryService queryService;

    public TraceQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TraceQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TraceQueryService.class);
        }
        return queryService;
    }

    public TraceBrief queryBasicTraces(final TraceQueryCondition condition) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        String traceId = Const.EMPTY_STRING;

        if (!Strings.isNullOrEmpty(condition.getTraceId())) {
            traceId = condition.getTraceId();
        } else if (nonNull(condition.getQueryDuration())) {
            startSecondTB = condition.getQueryDuration().getStartTimeBucketInSec();
            endSecondTB = condition.getQueryDuration().getEndTimeBucketInSec();
        } else {
            throw new UnexpectedException("The condition must contains either queryDuration or traceId.");
        }

        int minDuration = condition.getMinTraceDuration();
        int maxDuration = condition.getMaxTraceDuration();
        String endpointName = condition.getEndpointName();
        String endpointId = condition.getEndpointId();
        TraceState traceState = condition.getTraceState();
        QueryOrder queryOrder = condition.getQueryOrder();
        Pagination pagination = condition.getPaging();

        return getQueryService().queryBasicTraces(
            condition.getServiceId(), condition.getServiceInstanceId(), endpointId, traceId, endpointName, minDuration,
            maxDuration, traceState, queryOrder, pagination, startSecondTB, endSecondTB, condition.getTags()
        );
    }

    public Trace queryTrace(final String traceId) throws IOException {
        return getQueryService().queryTrace(traceId);
    }
}
