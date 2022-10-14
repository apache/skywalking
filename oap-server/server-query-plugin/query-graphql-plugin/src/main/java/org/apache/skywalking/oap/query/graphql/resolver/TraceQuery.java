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

import graphql.kickstart.tools.GraphQLQueryResolver;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Set;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.TagAutoCompleteQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Trace;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.isNull;

public class TraceQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TraceQueryService queryService;
    private TagAutoCompleteQueryService tagQueryService;

    public TraceQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TraceQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TraceQueryService.class);
        }
        return queryService;
    }

    private TagAutoCompleteQueryService getTagQueryService() {
        if (tagQueryService == null) {
            this.tagQueryService = moduleManager.find(CoreModule.NAME).provider().getService(TagAutoCompleteQueryService.class);
        }
        return tagQueryService;
    }

    public TraceBrief queryBasicTraces(final TraceQueryCondition condition) throws IOException {
        String traceId = Const.EMPTY_STRING;

        if (!Strings.isNullOrEmpty(condition.getTraceId())) {
            traceId = condition.getTraceId();
        } else if (isNull(condition.getQueryDuration())) {
            throw new UnexpectedException("The condition must contains either queryDuration or traceId.");
        }

        int minDuration = condition.getMinTraceDuration();
        int maxDuration = condition.getMaxTraceDuration();
        String endpointId = condition.getEndpointId();
        TraceState traceState = condition.getTraceState();
        QueryOrder queryOrder = condition.getQueryOrder();
        Pagination pagination = condition.getPaging();

        return getQueryService().queryBasicTraces(
            condition.getServiceId(), condition.getServiceInstanceId(), endpointId, traceId, minDuration,
            maxDuration, traceState, queryOrder, pagination, condition.getQueryDuration(), condition.getTags()
        );
    }

    public Trace queryTrace(final String traceId) throws IOException {
        return getQueryService().queryTrace(traceId);
    }

    public Set<String> queryTraceTagAutocompleteKeys(final Duration queryDuration) throws IOException {
        return getTagQueryService().queryTagAutocompleteKeys(TagType.TRACE, queryDuration);
    }

    public Set<String> queryTraceTagAutocompleteValues(final String tagKey, final Duration queryDuration) throws IOException {
        return getTagQueryService().queryTagAutocompleteValues(TagType.TRACE, tagKey, queryDuration);
    }
}
