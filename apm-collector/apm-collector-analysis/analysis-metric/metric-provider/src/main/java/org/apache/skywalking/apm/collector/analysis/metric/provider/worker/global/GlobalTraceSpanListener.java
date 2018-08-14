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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global;

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SegmentCoreInfo;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceSpanListener implements GlobalTraceIdsListener {

    private static final Logger logger = LoggerFactory.getLogger(GlobalTraceSpanListener.class);

    private final List<String> globalTraceIds = new LinkedList<>();
    private SegmentCoreInfo segmentCoreInfo;

    @Override public boolean containsPoint(Point point) {
        return Point.GlobalTraceIds.equals(point);
    }

    @Override public void parseGlobalTraceId(UniqueId uniqueId, SegmentCoreInfo segmentCoreInfo) {
        StringBuilder globalTraceIdBuilder = new StringBuilder();
        for (int i = 0; i < uniqueId.getIdPartsList().size(); i++) {
            if (i == 0) {
                globalTraceIdBuilder.append(uniqueId.getIdPartsList().get(i));
            } else {
                globalTraceIdBuilder.append(".").append(uniqueId.getIdPartsList().get(i));
            }
        }
        globalTraceIds.add(globalTraceIdBuilder.toString());
        this.segmentCoreInfo = segmentCoreInfo;
    }

    @Override public void build() {
        if (logger.isDebugEnabled()) {
            logger.debug("global trace listener build");
        }

        Graph<GlobalTrace> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.GLOBAL_TRACE_GRAPH_ID, GlobalTrace.class);
        globalTraceIds.forEach(globalTraceId -> {
            GlobalTrace globalTrace = new GlobalTrace();
            globalTrace.setId(segmentCoreInfo.getSegmentId() + Const.ID_SPLIT + globalTraceId);
            globalTrace.setTraceId(globalTraceId);
            globalTrace.setSegmentId(segmentCoreInfo.getSegmentId());
            globalTrace.setTimeBucket(segmentCoreInfo.getMinuteTimeBucket());
            graph.start(globalTrace);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/globalTraceSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new GlobalTraceSpanListener();
        }
    }
}
