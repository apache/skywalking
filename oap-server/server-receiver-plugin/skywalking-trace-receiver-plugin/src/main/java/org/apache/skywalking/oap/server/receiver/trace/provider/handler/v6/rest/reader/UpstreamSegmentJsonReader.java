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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.rest.reader;

import java.io.IOException;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

public class UpstreamSegmentJsonReader implements StreamJsonReader<UpstreamSegment.Builder> {

    private final SegmentJsonReader segmentJsonReader = new SegmentJsonReader();

    @Override
    public UpstreamSegment.Builder read(String json) throws IOException {
        UpstreamSegment.Builder upstreamSegmentBuilder = UpstreamSegment.newBuilder();
        ProtoBufJsonUtils.fromJSON(json, upstreamSegmentBuilder);

        SegmentObject.Builder segmentBuilder = segmentJsonReader.read(json);

        upstreamSegmentBuilder.setSegment(segmentBuilder.build().toByteString());

        return upstreamSegmentBuilder;
    }
}
