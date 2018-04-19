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

import org.apache.skywalking.apm.network.proto.SpanLayer;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.SpanType;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class SpanDecorator implements StandardBuilder {
    private boolean isOrigin = true;
    private StandardBuilder standardBuilder;
    private SpanObject spanObject;
    private SpanObject.Builder spanBuilder;
    private long startTimeMinuteTimeBucket = 0;
    private final ReferenceDecorator[] referenceDecorators;

    public SpanDecorator(SpanObject spanObject, StandardBuilder standardBuilder) {
        this.spanObject = spanObject;
        this.standardBuilder = standardBuilder;
        this.referenceDecorators = new ReferenceDecorator[spanObject.getRefsCount()];
    }

    public SpanDecorator(SpanObject.Builder spanBuilder, StandardBuilder standardBuilder) {
        this.spanBuilder = spanBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        this.referenceDecorators = new ReferenceDecorator[spanBuilder.getRefsCount()];
    }

    public int getSpanId() {
        if (isOrigin) {
            return spanObject.getSpanId();
        } else {
            return spanBuilder.getSpanId();
        }
    }

    public int getParentSpanId() {
        if (isOrigin) {
            return spanObject.getParentSpanId();
        } else {
            return spanBuilder.getParentSpanId();
        }
    }

    public SpanType getSpanType() {
        if (isOrigin) {
            return spanObject.getSpanType();
        } else {
            return spanBuilder.getSpanType();
        }
    }

    public int getSpanTypeValue() {
        if (isOrigin) {
            return spanObject.getSpanTypeValue();
        } else {
            return spanBuilder.getSpanTypeValue();
        }
    }

    public SpanLayer getSpanLayer() {
        if (isOrigin) {
            return spanObject.getSpanLayer();
        } else {
            return spanBuilder.getSpanLayer();
        }
    }

    public int getSpanLayerValue() {
        if (isOrigin) {
            return spanObject.getSpanLayerValue();
        } else {
            return spanBuilder.getSpanLayerValue();
        }
    }

    public void setStartTimeMinuteTimeBucket(long startTimeMinuteTimeBucket) {
        this.startTimeMinuteTimeBucket = startTimeMinuteTimeBucket;
    }

    public long getStartTimeMinuteTimeBucket() {
        return startTimeMinuteTimeBucket;
    }

    public long getStartTime() {
        if (isOrigin) {
            return spanObject.getStartTime();
        } else {
            return spanBuilder.getStartTime();
        }
    }

    public long getEndTime() {
        if (isOrigin) {
            return spanObject.getEndTime();
        } else {
            return spanBuilder.getEndTime();
        }
    }

    public int getComponentId() {
        if (isOrigin) {
            return spanObject.getComponentId();
        } else {
            return spanBuilder.getComponentId();
        }
    }

    public void setComponentId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setComponentId(value);
    }

    public String getComponent() {
        if (isOrigin) {
            return spanObject.getComponent();
        } else {
            return spanBuilder.getComponent();
        }
    }

    public void setComponent(String value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setComponent(value);
    }

    public int getPeerId() {
        if (isOrigin) {
            return spanObject.getPeerId();
        } else {
            return spanBuilder.getPeerId();
        }
    }

    public void setPeerId(int peerId) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setPeerId(peerId);
    }

    public String getPeer() {
        if (isOrigin) {
            return spanObject.getPeer();
        } else {
            return spanBuilder.getPeer();
        }
    }

    public void setPeer(String peer) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setPeer(peer);
    }

    public int getOperationNameId() {
        if (isOrigin) {
            return spanObject.getOperationNameId();
        } else {
            return spanBuilder.getOperationNameId();
        }
    }

    public void setOperationNameId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setOperationNameId(value);
    }

    public String getOperationName() {
        if (isOrigin) {
            return spanObject.getOperationName();
        } else {
            return spanBuilder.getOperationName();
        }
    }

    public void setOperationName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilder.setOperationName(value);
    }

    public boolean getIsError() {
        if (isOrigin) {
            return spanObject.getIsError();
        } else {
            return spanBuilder.getIsError();
        }
    }

    public int getRefsCount() {
        if (isOrigin) {
            return spanObject.getRefsCount();
        } else {
            return spanBuilder.getRefsCount();
        }
    }

    public ReferenceDecorator getRefs(int index) {
        if (isNull(referenceDecorators[index])) {
            if (isOrigin) {
                referenceDecorators[index] = new ReferenceDecorator(spanObject.getRefs(index), this);
            } else {
                referenceDecorators[index] = new ReferenceDecorator(spanBuilder.getRefsBuilder(index), this);
            }
        }
        return referenceDecorators[index];
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            spanBuilder = spanObject.toBuilder();
            standardBuilder.toBuilder();
        }
    }
}
