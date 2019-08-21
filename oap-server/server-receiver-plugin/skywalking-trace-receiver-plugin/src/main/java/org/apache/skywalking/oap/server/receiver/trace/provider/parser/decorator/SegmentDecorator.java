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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.decorator;

import org.apache.skywalking.apm.network.language.agent.TraceSegmentObject;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class SegmentDecorator implements StandardBuilder {
    private boolean isOrigin = true;
    private final TraceSegmentObject segmentObject;
    private TraceSegmentObject.Builder segmentBuilder;
    private final boolean isV2;
    private final SegmentObject segmentObjectV2;
    private SegmentObject.Builder segmentBuilderV2;
    private final SpanDecorator[] spanDecorators;

    public SegmentDecorator(TraceSegmentObject segmentObject) {
        this.segmentObject = segmentObject;
        this.segmentObjectV2 = null;
        this.spanDecorators = new SpanDecorator[segmentObject.getSpansCount()];
        isV2 = false;
    }

    public SegmentDecorator(SegmentObject segmentObjectV2) {
        this.segmentObjectV2 = segmentObjectV2;
        this.segmentObject = null;
        this.spanDecorators = new SpanDecorator[segmentObjectV2.getSpansCount()];
        isV2 = true;
    }

    public int getServiceId() {
        return isV2 ? segmentObjectV2.getServiceId() : segmentObject.getApplicationId();
    }

    public int getServiceInstanceId() {
        return isV2 ? segmentObjectV2.getServiceInstanceId() : segmentObject.getApplicationInstanceId();
    }

    public UniqueId getTraceSegmentId() {
        return isV2 ? segmentObjectV2.getTraceSegmentId() : segmentObject.getTraceSegmentId();
    }

    public int getSpansCount() {
        return isV2 ? segmentObjectV2.getSpansCount() : segmentObject.getSpansCount();
    }

    public SpanDecorator getSpans(int index) {
        if (isNull(spanDecorators[index])) {
            if (isOrigin) {
                if (isV2) {
                    spanDecorators[index] = new SpanDecorator(segmentObjectV2.getSpans(index), this);
                } else {
                    spanDecorators[index] = new SpanDecorator(segmentObject.getSpans(index), this);
                }
            } else {
                if (isV2) {
                    spanDecorators[index] = new SpanDecorator(segmentBuilderV2.getSpansBuilder(index), this);
                } else {
                    spanDecorators[index] = new SpanDecorator(segmentBuilder.getSpansBuilder(index), this);
                }
            }
        }
        return spanDecorators[index];
    }

    public byte[] toByteArray() {
        if (isOrigin) {
            return isV2 ? segmentObjectV2.toByteArray() : segmentObject.toByteArray();
        } else {
            return segmentBuilder.build().toByteArray();
        }
    }

    @Override public void toBuilder() {
        if (isOrigin) {
            this.isOrigin = false;
            if (isV2) {
                this.segmentBuilderV2 = segmentObjectV2.toBuilder();
            } else {
                this.segmentBuilder = segmentObject.toBuilder();
            }
        }
    }
}
