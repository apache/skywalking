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

package org.apache.skywalking.oap.server.receiver.zipkin.data;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;

/**
 * Each SkyWalkingTrace consists of segments in each application, original from {@link ZipkinTrace}s
 */
public class SkyWalkingTrace {
    private UniqueId globalTraceId;
    private List<SegmentObject.Builder> segmentList;

    public SkyWalkingTrace(UniqueId globalTraceId, List<SegmentObject.Builder> segmentList) {
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

    public List<SegmentObject.Builder> getSegmentList() {
        return segmentList;
    }
}
