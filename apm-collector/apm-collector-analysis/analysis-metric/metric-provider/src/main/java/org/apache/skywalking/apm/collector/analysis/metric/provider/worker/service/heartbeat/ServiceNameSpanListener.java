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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.heartbeat;

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.*;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;

/**
 * @author peng-yongsheng
 */
public class ServiceNameSpanListener implements EntrySpanListener, ExitSpanListener, LocalSpanListener {

    private final List<ServiceName> serviceNames;

    private ServiceNameSpanListener() {
        this.serviceNames = new LinkedList<>();
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point) || Point.Local.equals(point);
    }

    @Override public void parseEntry(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        ServiceName serviceName = new ServiceName();
        serviceName.setId(String.valueOf(spanDecorator.getOperationNameId()));
        serviceName.setHeartBeatTime(segmentCoreInfo.getStartTime());
        serviceNames.add(serviceName);

        for (int i = 0; i < spanDecorator.getRefsCount(); i++) {
            ReferenceDecorator referenceDecorator = spanDecorator.getRefs(i);

            ServiceName entryServiceName = new ServiceName();
            entryServiceName.setId(String.valueOf(referenceDecorator.getEntryServiceId()));
            entryServiceName.setHeartBeatTime(segmentCoreInfo.getStartTime());
            serviceNames.add(entryServiceName);

            ServiceName parentServiceName = new ServiceName();
            parentServiceName.setId(String.valueOf(referenceDecorator.getParentServiceId()));
            parentServiceName.setHeartBeatTime(segmentCoreInfo.getStartTime());
            serviceNames.add(parentServiceName);
        }
    }

    @Override public void parseExit(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        ServiceName serviceName = new ServiceName();
        serviceName.setId(String.valueOf(spanDecorator.getOperationNameId()));
        serviceName.setHeartBeatTime(segmentCoreInfo.getStartTime());
        serviceNames.add(serviceName);
    }

    @Override public void parseLocal(SpanDecorator spanDecorator, SegmentCoreInfo segmentCoreInfo) {
        ServiceName serviceName = new ServiceName();
        serviceName.setId(String.valueOf(spanDecorator.getOperationNameId()));
        serviceName.setHeartBeatTime(segmentCoreInfo.getStartTime());
        serviceNames.add(serviceName);
    }

    @Override public void build() {
        Graph<ServiceName> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.SERVICE_HEART_BEAT_PERSISTENCE_GRAPH_ID, ServiceName.class);
        serviceNames.forEach(graph::start);
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/serviceNameSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new ServiceNameSpanListener();
        }
    }
}
