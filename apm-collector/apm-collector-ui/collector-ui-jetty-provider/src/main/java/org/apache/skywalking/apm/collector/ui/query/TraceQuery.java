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

import java.text.ParseException;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.ui.trace.Trace;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceQueryCondition;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.SegmentTopService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class TraceQuery implements Query {

    private final ModuleManager moduleManager;
    private SegmentTopService segmentTopService;

    public TraceQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private SegmentTopService getSegmentTopService() {
        if (ObjectUtils.isEmpty(segmentTopService)) {
            this.segmentTopService = new SegmentTopService(moduleManager);
        }
        return segmentTopService;
    }

    public TraceBrief queryBasicTraces(TraceQueryCondition condition) throws ParseException {
        long start = DurationUtils.INSTANCE.durationToSecondTimeBucket(condition.getQueryDuration().getStep(), condition.getQueryDuration().getStart());
        long end = DurationUtils.INSTANCE.durationToSecondTimeBucket(condition.getQueryDuration().getStep(), condition.getQueryDuration().getEnd());

        long minDuration = condition.getMinTraceDuration();
        long maxDuration = condition.getMaxTraceDuration();
        String operationName = condition.getOperationName();
        String traceId = condition.getTraceId();
        int applicationId = condition.getApplicationId();
        int limit = condition.getPaging().getPageSize();
        int from = condition.getPaging().getPageSize() * condition.getPaging().getPageNum();

        return segmentTopService.loadTop(start, end, minDuration, maxDuration, operationName, traceId, applicationId, limit, from);
    }

    public Trace queryTrace(String id) {
        return null;
    }
}
