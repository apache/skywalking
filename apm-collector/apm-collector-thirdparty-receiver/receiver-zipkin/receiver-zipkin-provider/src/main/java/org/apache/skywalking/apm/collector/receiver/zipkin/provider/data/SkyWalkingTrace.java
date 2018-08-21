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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.data;

import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;

import java.util.LinkedList;
import java.util.List;

/**
 * Each SkyWalkingTrace consists of segments in each application, original from {@link ZipkinTrace}s
 */
public class SkyWalkingTrace {
    private UniqueId globalTraceId;
    private List<TraceSegmentObject.Builder> segmentList;

    public SkyWalkingTrace(UniqueId globalTraceId, List<TraceSegmentObject.Builder> segmentList) {
        this.globalTraceId = globalTraceId;
        this.segmentList = segmentList;
    }

    public List<UpstreamSegment.Builder> toUpstreamSegment() {
        List<UpstreamSegment.Builder> newUpstreamList = new LinkedList<>();
        segmentList.forEach(segment -> {
            UpstreamSegment.Builder builder = UpstreamSegment.newBuilder();
            builder.addGlobalTraceIds(globalTraceId);
            builder.setSegment(segment.build().toByteString());
            newUpstreamList.add(builder);
        });
        return newUpstreamList;
    }

    public UniqueId getGlobalTraceId() {
        return globalTraceId;
    }

    public List<TraceSegmentObject.Builder> getSegmentList() {
        return segmentList;
    }
}
