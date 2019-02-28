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

import org.apache.skywalking.apm.network.language.agent.RefType;
import org.apache.skywalking.apm.network.language.agent.TraceSegmentReference;
import org.apache.skywalking.apm.network.language.agent.UniqueId;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;

/**
 * @author peng-yongsheng
 */
public class ReferenceDecorator implements StandardBuilder {

    private boolean isOrigin = true;
    private StandardBuilder standardBuilder;
    private TraceSegmentReference referenceObject;
    private TraceSegmentReference.Builder referenceBuilder;
    private final boolean isV2;
    private SegmentReference referenceObjectV2;
    private SegmentReference.Builder referenceBuilderV2;

    public ReferenceDecorator(TraceSegmentReference referenceObject, StandardBuilder standardBuilder) {
        this.referenceObject = referenceObject;
        this.standardBuilder = standardBuilder;
        isV2 = false;
    }

    public ReferenceDecorator(TraceSegmentReference.Builder referenceBuilder, StandardBuilder standardBuilder) {
        this.referenceBuilder = referenceBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        isV2 = false;
    }

    public ReferenceDecorator(SegmentReference referenceObject, StandardBuilder standardBuilder) {
        this.referenceObjectV2 = referenceObject;
        this.standardBuilder = standardBuilder;
        isV2 = true;
    }

    public ReferenceDecorator(SegmentReference.Builder referenceBuilder, StandardBuilder standardBuilder) {
        this.referenceBuilderV2 = referenceBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
        isV2 = true;
    }

    public RefType getRefType() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getRefType() : referenceObject.getRefType();
        } else {
            return isV2 ? referenceBuilderV2.getRefType() : referenceBuilder.getRefType();
        }
    }

    public int getRefTypeValue() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getRefTypeValue() : referenceObject.getRefTypeValue();
        } else {
            return isV2 ? referenceBuilderV2.getRefTypeValue() : referenceBuilder.getRefTypeValue();
        }
    }

    public int getEntryEndpointId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getEntryEndpointId() : referenceObject.getEntryServiceId();
        } else {
            return isV2 ? referenceBuilderV2.getEntryEndpointId() : referenceBuilder.getEntryServiceId();
        }
    }

    public void setEntryEndpointId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setEntryEndpointId(value);
        } else {
            referenceBuilder.setEntryServiceId(value);
        }
    }

    public String getEntryEndpointName() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getEntryEndpoint() : referenceObject.getEntryServiceName();
        } else {
            return isV2 ? referenceBuilderV2.getEntryEndpoint() : referenceBuilder.getEntryServiceName();
        }
    }

    public void setEntryEndpointName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setEntryEndpoint(value);
        } else {
            referenceBuilder.setEntryServiceName(value);
        }
    }

    public int getEntryServiceInstanceId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getEntryServiceInstanceId() : referenceObject.getEntryApplicationInstanceId();
        } else {
            return isV2 ? referenceBuilderV2.getEntryServiceInstanceId() : referenceBuilder.getEntryApplicationInstanceId();
        }
    }

    public int getParentServiceInstanceId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getParentServiceInstanceId() : referenceObject.getParentApplicationInstanceId();
        } else {
            return isV2 ? referenceBuilderV2.getParentServiceInstanceId() : referenceBuilder.getParentApplicationInstanceId();
        }
    }

    public int getParentEndpointId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getParentEndpointId() : referenceObject.getParentServiceId();
        } else {
            return isV2 ? referenceBuilderV2.getParentEndpointId() : referenceBuilder.getParentServiceId();
        }
    }

    public void setParentEndpointId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setParentEndpointId(value);
        } else {
            referenceBuilder.setParentServiceId(value);
        }
    }

    public int getParentSpanId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getParentSpanId() : referenceObject.getParentSpanId();
        } else {
            return isV2 ? referenceBuilderV2.getParentSpanId() : referenceBuilder.getParentSpanId();
        }
    }

    public String getParentEndpointName() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getParentEndpoint() : referenceObject.getParentServiceName();
        } else {
            return isV2 ? referenceBuilderV2.getParentEndpoint() : referenceBuilder.getParentServiceName();
        }
    }

    public void setParentEndpointName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setParentEndpoint(value);
        } else {
            referenceBuilder.setParentServiceName(value);
        }
    }

    public UniqueId getParentTraceSegmentId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getParentTraceSegmentId() : referenceObject.getParentTraceSegmentId();
        } else {
            return isV2 ? referenceBuilderV2.getParentTraceSegmentId() : referenceBuilder.getParentTraceSegmentId();
        }
    }

    public int getNetworkAddressId() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getNetworkAddressId() : referenceObject.getNetworkAddressId();
        } else {
            return isV2 ? referenceBuilderV2.getNetworkAddressId() : referenceBuilder.getNetworkAddressId();
        }
    }

    public void setNetworkAddressId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setNetworkAddressId(value);
        } else {
            referenceBuilder.setNetworkAddressId(value);
        }
    }

    public String getNetworkAddress() {
        if (isOrigin) {
            return isV2 ? referenceObjectV2.getNetworkAddress() : referenceObject.getNetworkAddress();
        } else {
            return isV2 ? referenceBuilderV2.getNetworkAddress() : referenceBuilder.getNetworkAddress();
        }
    }

    public void setNetworkAddress(String value) {
        if (isOrigin) {
            toBuilder();
        }
        if (isV2) {
            referenceBuilderV2.setNetworkAddress(value);
        } else {
            referenceBuilder.setNetworkAddress(value);
        }
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            if (isV2) {
                referenceBuilderV2 = referenceObjectV2.toBuilder();
            } else {
                referenceBuilder = referenceObject.toBuilder();
            }
            standardBuilder.toBuilder();
        }
    }
}
