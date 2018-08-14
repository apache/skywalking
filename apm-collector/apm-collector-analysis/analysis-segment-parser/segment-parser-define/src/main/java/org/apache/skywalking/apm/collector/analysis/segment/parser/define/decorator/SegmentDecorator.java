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

package org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator;

import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.apache.skywalking.apm.network.proto.UniqueId;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class SegmentDecorator implements StandardBuilder {
    private boolean isOrigin = true;
    private final TraceSegmentObject segmentObject;
    private TraceSegmentObject.Builder segmentBuilder;
    private final SpanDecorator[] spanDecorators;

    public SegmentDecorator(TraceSegmentObject segmentObject) {
        this.segmentObject = segmentObject;
        this.spanDecorators = new SpanDecorator[segmentObject.getSpansCount()];
    }

    public int getApplicationId() {
        return segmentObject.getApplicationId();
    }

    public int getApplicationInstanceId() {
        return segmentObject.getApplicationInstanceId();
    }

    public UniqueId getTraceSegmentId() {
        return segmentObject.getTraceSegmentId();
    }

    public int getSpansCount() {
        return segmentObject.getSpansCount();
    }

    public SpanDecorator getSpans(int index) {
        if (isNull(spanDecorators[index])) {
            if (isOrigin) {
                spanDecorators[index] = new SpanDecorator(segmentObject.getSpans(index), this);
            } else {
                spanDecorators[index] = new SpanDecorator(segmentBuilder.getSpansBuilder(index), this);
            }
        }
        return spanDecorators[index];
    }

    public byte[] toByteArray() {
        if (isOrigin) {
            return segmentObject.toByteArray();
        } else {
            return segmentBuilder.build().toByteArray();
        }
    }

    @Override public void toBuilder() {
        if (isOrigin) {
            this.isOrigin = false;
            this.segmentBuilder = segmentObject.toBuilder();
        }
    }
}
