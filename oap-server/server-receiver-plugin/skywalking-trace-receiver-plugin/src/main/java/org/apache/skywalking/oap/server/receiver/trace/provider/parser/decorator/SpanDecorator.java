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

import org.apache.skywalking.apm.network.language.agent.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.SpanObject;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class SpanDecorator implements StandardBuilder {
    private final boolean isV2;
    private boolean isOrigin = true;
    private StandardBuilder standardBuilder;
    private SpanObject spanObject;
    private SpanObjectV2 spanObjectV2;
    private SpanObject.Builder spanBuilder;
    private SpanObjectV2.Builder spanBuilderV2;
    private final ReferenceDecorator[] referenceDecorators;

    public SpanDecorator(SpanObject spanObject, StandardBuilder standardBuilder) {
        this.spanObject = spanObject;
        this.standardBuilder = standardBuilder;
        this.referenceDecorators = new ReferenceDecorator[spanObject.getRefsCount()];
        this.isV2 = false;
    }

    public SpanDecorator(SpanObject.Builder spanBuilder, StandardBuilder standardBuilder) {
        this.spanBuilder = spanBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        this.referenceDecorators = new ReferenceDecorator[spanBuilder.getRefsCount()];
        this.isV2 = false;
    }

    public SpanDecorator(SpanObjectV2 spanObject, StandardBuilder standardBuilder) {
        this.spanObjectV2 = spanObject;
        this.standardBuilder = standardBuilder;
        this.referenceDecorators = new ReferenceDecorator[spanObject.getRefsCount()];
        this.isV2 = true;
    }

    public SpanDecorator(SpanObjectV2.Builder spanBuilder, StandardBuilder standardBuilder) {
        this.spanBuilderV2 = spanBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        this.referenceDecorators = new ReferenceDecorator[spanBuilder.getRefsCount()];
        this.isV2 = true;
    }

    public int getSpanId() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getSpanId() : spanObject.getSpanId();
        } else {
            return isV2 ? spanBuilderV2.getSpanId() : spanBuilder.getSpanId();
        }
    }

    public int getParentSpanId() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getParentSpanId() : spanObject.getParentSpanId();
        } else {
            return isV2 ? spanBuilderV2.getParentSpanId() : spanBuilder.getParentSpanId();
        }
    }

    public SpanType getSpanType() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getSpanType() : spanObject.getSpanType();
        } else {
            return isV2 ? spanBuilderV2.getSpanType() : spanBuilder.getSpanType();
        }
    }

    public int getSpanTypeValue() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getSpanTypeValue() : spanObject.getSpanTypeValue();
        } else {
            return isV2 ? spanBuilderV2.getSpanTypeValue() : spanBuilder.getSpanTypeValue();
        }
    }

    public SpanLayer getSpanLayer() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getSpanLayer() : spanObject.getSpanLayer();
        } else {
            return isV2 ? spanBuilderV2.getSpanLayer() : spanBuilder.getSpanLayer();
        }
    }

    public int getSpanLayerValue() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getSpanLayerValue() : spanObject.getSpanLayerValue();
        } else {
            return isV2 ? spanBuilderV2.getSpanLayerValue() : spanBuilder.getSpanLayerValue();
        }
    }

    public long getStartTime() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getStartTime() : spanObject.getStartTime();
        } else {
            return isV2 ? spanBuilderV2.getStartTime() : spanBuilder.getStartTime();
        }
    }

    public long getEndTime() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getEndTime() : spanObject.getEndTime();
        } else {
            return isV2 ? spanBuilderV2.getEndTime() : spanBuilder.getEndTime();
        }
    }

    public int getComponentId() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getComponentId() : spanObject.getComponentId();
        } else {
            return isV2 ? spanBuilderV2.getComponentId() : spanBuilder.getComponentId();
        }
    }

    public void setComponentId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setComponentId(value);
        } else {
            spanBuilder.setComponentId(value);
        }
    }

    public String getComponent() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getComponent() : spanObject.getComponent();
        } else {
            return isV2 ? spanBuilderV2.getComponent() : spanBuilder.getComponent();
        }
    }

    public void setComponent(String value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setComponent(value);
        } else {
            spanBuilder.setComponent(value);
        }
    }

    public int getPeerId() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getPeerId() : spanObject.getPeerId();
        } else {
            return isV2 ? spanBuilderV2.getPeerId() : spanBuilder.getPeerId();
        }
    }

    public void setPeerId(int peerId) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setPeerId(peerId);
        } else {
            spanBuilder.setPeerId(peerId);
        }
    }

    public String getPeer() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getPeer() : spanObject.getPeer();
        } else {
            return isV2 ? spanBuilderV2.getPeer() : spanBuilder.getPeer();
        }
    }

    public void setPeer(String peer) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setPeer(peer);
        } else {
            spanBuilder.setPeer(peer);
        }
    }

    public int getOperationNameId() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getOperationNameId() : spanObject.getOperationNameId();
        } else {
            return isV2 ? spanBuilderV2.getOperationNameId() : spanBuilder.getOperationNameId();
        }
    }

    public void setOperationNameId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setOperationNameId(value);
        } else {
            spanBuilder.setOperationNameId(value);
        }
    }

    public String getOperationName() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getOperationName() : spanObject.getOperationName();
        } else {
            return isV2 ? spanBuilderV2.getOperationName() : spanBuilder.getOperationName();
        }
    }

    public void setOperationName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            spanBuilderV2.setOperationName(value);
        } else {
            spanBuilder.setOperationName(value);
        }
    }

    public boolean getIsError() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getIsError() : spanObject.getIsError();
        } else {
            return isV2 ? spanBuilderV2.getIsError() : spanBuilder.getIsError();
        }
    }

    public int getRefsCount() {
        if (isOrigin) {
            return isV2 ? spanObjectV2.getRefsCount() : spanObject.getRefsCount();
        } else {
            return isV2 ? spanBuilderV2.getRefsCount() : spanBuilder.getRefsCount();
        }
    }

    public ReferenceDecorator getRefs(int index) {
        if (isNull(referenceDecorators[index])) {
            if (isOrigin) {
                if (isV2) {
                    referenceDecorators[index] = new ReferenceDecorator(spanObjectV2.getRefs(index), this);
                } else {
                    referenceDecorators[index] = new ReferenceDecorator(spanObject.getRefs(index), this);
                }
            } else {
                if (isV2) {
                    referenceDecorators[index] = new ReferenceDecorator(spanBuilderV2.getRefsBuilder(index), this);
                } else {
                    referenceDecorators[index] = new ReferenceDecorator(spanBuilder.getRefsBuilder(index), this);
                }
            }
        }
        return referenceDecorators[index];
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            if (isV2) {
                spanBuilderV2 = spanObjectV2.toBuilder();
            } else {
                spanBuilder = spanObject.toBuilder();
            }
            standardBuilder.toBuilder();
        }
    }
}
