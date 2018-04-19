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

package org.apache.skywalking.apm.collector.ui.query;

import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.ui.trace.Trace;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceQueryCondition;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.SegmentTopService;
import org.apache.skywalking.apm.collector.ui.service.TraceStackService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.apache.skywalking.apm.collector.ui.utils.PaginationUtils;

import java.text.ParseException;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class TraceQuery implements Query {

    private final ModuleManager moduleManager;
    private SegmentTopService segmentTopService;
    private TraceStackService traceStackService;

    public TraceQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private SegmentTopService getSegmentTopService() {
        if (isNull(segmentTopService)) {
            this.segmentTopService = new SegmentTopService(moduleManager);
        }
        return segmentTopService;
    }

    private TraceStackService getTraceStackService() {
        if (isNull(traceStackService)) {
            this.traceStackService = new TraceStackService(moduleManager);
        }
        return traceStackService;
    }

    public TraceBrief queryBasicTraces(TraceQueryCondition condition) throws ParseException {
        long startSecondTimeBucket = 0;
        long endSecondTimeBucket = 0;
        String traceId = Const.EMPTY_STRING;

        if (StringUtils.isNotEmpty(condition.getTraceId())) {
            traceId = condition.getTraceId();
        } else if (nonNull(condition.getQueryDuration())) {
            startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(condition.getQueryDuration().getStep(), condition.getQueryDuration().getStart());
            endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(condition.getQueryDuration().getStep(), condition.getQueryDuration().getEnd());
        } else {
            throw new UnexpectedException("The condition must contains either queryDuration or traceId.");
        }

        long minDuration = condition.getMinTraceDuration();
        long maxDuration = condition.getMaxTraceDuration();
        String operationName = condition.getOperationName();
        int applicationId = condition.getApplicationId();

        PaginationUtils.Page page = PaginationUtils.INSTANCE.exchange(condition.getPaging());
        return getSegmentTopService().loadTop(startSecondTimeBucket, endSecondTimeBucket, minDuration, maxDuration, operationName, traceId, applicationId, page.getLimit(), page.getFrom());
    }

    public Trace queryTrace(String traceId) {
        return getTraceStackService().load(traceId);
    }
}
