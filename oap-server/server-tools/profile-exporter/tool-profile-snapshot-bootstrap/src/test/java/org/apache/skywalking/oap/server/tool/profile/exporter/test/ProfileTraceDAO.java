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

package org.apache.skywalking.oap.server.tool.profile.exporter.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;

public class ProfileTraceDAO implements ITraceQueryDAO {
    private final ExportedData exportData;

    public ProfileTraceDAO(ExportedData exportData) {
        this.exportData = exportData;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB,
                                       long endSecondTB,
                                       long minDuration,
                                       long maxDuration,
                                       String endpointName,
                                       String serviceId,
                                       String serviceInstanceId,
                                       String endpointId,
                                       String traceId,
                                       int limit,
                                       int from,
                                       TraceState traceState,
                                       QueryOrder queryOrder,
                                       final List<Tag> tags) throws IOException {
        return null;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        final ArrayList<SegmentRecord> segments = new ArrayList<>();
        final SegmentRecord segment = new SegmentRecord();
        segments.add(segment);

        final SegmentObject.Builder segmentBuilder = SegmentObject.newBuilder();
        segmentBuilder.setTraceId(traceId);
        segmentBuilder.setTraceSegmentId(exportData.getSegmentId());
        for (ExportedData.Span span : exportData.getSpans()) {
            segmentBuilder.addSpans(SpanObject.newBuilder()
                                              .setOperationName(span.getOperation())
                                              .setStartTime(span.getStart())
                                              .setEndTime(span.getEnd())
                                              .setSpanId(span.getId())
                                              .setParentSpanId(span.getParentId()));
        }
        segment.setDataBinary(segmentBuilder.build().toByteArray());
        segment.setTraceId(exportData.getTraceId());
        segment.setServiceId("service");
        segment.setSegmentId(exportData.getSegmentId());
        return segments;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) throws IOException {
        return null;
    }
}
