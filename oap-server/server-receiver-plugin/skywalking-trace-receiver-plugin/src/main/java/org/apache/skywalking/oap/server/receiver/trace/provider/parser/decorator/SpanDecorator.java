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

import java.util.List;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class SpanDecorator implements StandardBuilder {
    private boolean isOrigin = true;
    private final StandardBuilder standardBuilder;
    private SpanObjectV2 spanObjectV2;
    private SpanObjectV2.Builder spanBuilderV2;
    private final ReferenceDecorator[] referenceDecorators;

    public SpanDecorator(SpanObjectV2 spanObject, StandardBuilder standardBuilder) {
        this.spanObjectV2 = spanObject;
        this.standardBuilder = standardBuilder;
        this.referenceDecorators = new ReferenceDecorator[spanObject.getRefsCount()];
    }

    public SpanDecorator(SpanObjectV2.Builder spanBuilder, StandardBuilder standardBuilder) {
        this.spanBuilderV2 = spanBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        this.referenceDecorators = new ReferenceDecorator[spanBuilder.getRefsCount()];
    }

    public int getSpanId() {
        if (isOrigin) {
            return spanObjectV2.getSpanId();
        } else {
            return spanBuilderV2.getSpanId();
        }
    }

    public SpanType getSpanType() {
        if (isOrigin) {
            return spanObjectV2.getSpanType();
        } else {
            return spanBuilderV2.getSpanType();
        }
    }

    public SpanLayer getSpanLayer() {
        if (isOrigin) {
            return spanObjectV2.getSpanLayer();
        } else {
            return spanBuilderV2.getSpanLayer();
        }
    }

    public int getSpanLayerValue() {
        if (isOrigin) {
            return spanObjectV2.getSpanLayerValue();
        } else {
            return spanBuilderV2.getSpanLayerValue();
        }
    }

    public long getStartTime() {
        if (isOrigin) {
            return spanObjectV2.getStartTime();
        } else {
            return spanBuilderV2.getStartTime();
        }
    }

    public long getEndTime() {
        if (isOrigin) {
            return spanObjectV2.getEndTime();
        } else {
            return spanBuilderV2.getEndTime();
        }
    }

    public int getComponentId() {
        if (isOrigin) {
            return spanObjectV2.getComponentId();
        } else {
            return spanBuilderV2.getComponentId();
        }
    }

    public void setComponentId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setComponentId(value);
    }

    public String getComponent() {
        if (isOrigin) {
            return spanObjectV2.getComponent();
        } else {
            return spanBuilderV2.getComponent();
        }
    }

    public void setComponent(String value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setComponent(value);
    }

    public int getPeerId() {
        if (isOrigin) {
            return spanObjectV2.getPeerId();
        } else {
            return spanBuilderV2.getPeerId();
        }
    }

    public void setPeerId(int peerId) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setPeerId(peerId);
    }

    public String getPeer() {
        if (isOrigin) {
            return spanObjectV2.getPeer();
        } else {
            return spanBuilderV2.getPeer();
        }
    }

    public void setPeer(String peer) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setPeer(peer);
    }

    public int getOperationNameId() {
        if (isOrigin) {
            return spanObjectV2.getOperationNameId();
        } else {
            return spanBuilderV2.getOperationNameId();
        }
    }

    public void setOperationNameId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setOperationNameId(value);
    }

    public String getOperationName() {
        if (isOrigin) {
            return spanObjectV2.getOperationName();
        } else {
            return spanBuilderV2.getOperationName();
        }
    }

    public void setOperationName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        spanBuilderV2.setOperationName(value);
    }

    public boolean getIsError() {
        if (isOrigin) {
            return spanObjectV2.getIsError();
        } else {
            return spanBuilderV2.getIsError();
        }
    }

    public int getRefsCount() {
        if (isOrigin) {
            return spanObjectV2.getRefsCount();
        } else {
            return spanBuilderV2.getRefsCount();
        }
    }

    public ReferenceDecorator getRefs(int index) {
        if (isNull(referenceDecorators[index])) {
            if (isOrigin) {
                referenceDecorators[index] = new ReferenceDecorator(spanObjectV2.getRefs(index), this);
            } else {
                referenceDecorators[index] = new ReferenceDecorator(spanBuilderV2.getRefsBuilder(index), this);
            }
        }
        return referenceDecorators[index];
    }

    public List<KeyStringValuePair> getAllTags() {
        if (isOrigin) {
            return spanObjectV2.getTagsList();
        } else {
            return spanBuilderV2.getTagsList();
        }
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            spanBuilderV2 = spanObjectV2.toBuilder();
            standardBuilder.toBuilder();
        }
    }

}
