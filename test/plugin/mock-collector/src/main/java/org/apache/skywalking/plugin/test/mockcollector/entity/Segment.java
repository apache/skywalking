/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.mockcollector.entity;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import org.apache.skywalking.apm.network.language.agent.UniqueId;

@ToString
@Builder
@AllArgsConstructor
public class Segment {
    private String segmentId;
    private List<Span> spans;

    public static class SegmentBuilder {

        public SegmentBuilder addSpan(Span.SpanBuilder spanBuilder) {
            if (spans == null) {
                this.spans = new ArrayList<>();
            }

            spans.add(spanBuilder.build());
            return this;
        }

        public SegmentBuilder segmentId(UniqueId segmentUniqueId) {
            segmentId = String.join(".", Long.toString(segmentUniqueId.getIdParts(0)), Long.toString(segmentUniqueId.getIdParts(1)), Long.toString(segmentUniqueId.getIdParts(2)));
            return this;
        }
    }

}
