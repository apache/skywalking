/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.worker.segment.buffer;

import org.skywalking.apm.collector.agentstream.worker.segment.standardization.SegmentStandardizationWorker;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.network.proto.SpanLayer;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.SpanType;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UniqueId;
import org.skywalking.apm.network.proto.UpstreamSegment;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * @author peng-yongsheng
 */
public class SegmentBufferWriteWorkerTestCase {

    public static void main(String[] args) throws WorkerException, ProviderNotFoundException {
        SegmentBufferConfig.BUFFER_PATH = "/Users/peng-yongsheng/code/sky-walking/sky-walking/apm-collector/buffer/";

        SegmentStandardizationWorker worker = new SegmentStandardizationWorker(null, null);
        worker.preStart();
        worker.allocateJob(buildSegment());
        worker.allocateJob(buildSegment());
        worker.allocateJob(buildSegment());
        worker.allocateJob(buildSegment());
        worker.allocateJob(buildSegment());
    }

    private static UpstreamSegment buildSegment() {
        long now = System.currentTimeMillis();

        int id = 1;
        UniqueId.Builder builder = UniqueId.newBuilder();
        builder.addIdParts(id);
        builder.addIdParts(id);
        builder.addIdParts(id);
        UniqueId segmentId = builder.build();

        UpstreamSegment.Builder upstream = UpstreamSegment.newBuilder();
        upstream.addGlobalTraceIds(segmentId);

        TraceSegmentObject.Builder segmentBuilder = TraceSegmentObject.newBuilder();
        segmentBuilder.setApplicationId(1);
        segmentBuilder.setApplicationInstanceId(1);
        segmentBuilder.setTraceSegmentId(segmentId);

        SpanObject.Builder entrySpan = SpanObject.newBuilder();
        entrySpan.setSpanId(0);
        entrySpan.setSpanType(SpanType.Entry);
        entrySpan.setSpanLayer(SpanLayer.Http);
        entrySpan.setParentSpanId(-1);
        entrySpan.setStartTime(now);
        entrySpan.setEndTime(now + 3000);
        entrySpan.setComponentId(ComponentsDefine.TOMCAT.getId());
        entrySpan.setOperationNameId(1);
        entrySpan.setIsError(false);
        segmentBuilder.addSpans(entrySpan);

        SpanObject.Builder exitSpan = SpanObject.newBuilder();
        exitSpan.setSpanId(1);
        exitSpan.setSpanType(SpanType.Exit);
        exitSpan.setSpanLayer(SpanLayer.Database);
        exitSpan.setParentSpanId(0);
        exitSpan.setStartTime(now);
        exitSpan.setEndTime(now + 3000);
        exitSpan.setComponentId(ComponentsDefine.MONGODB.getId());
        exitSpan.setOperationNameId(2);
        exitSpan.setIsError(false);
        exitSpan.setPeer("localhost:8888");
        segmentBuilder.addSpans(exitSpan);

        upstream.setSegment(segmentBuilder.build().toByteString());
        return upstream.build();
    }
}
