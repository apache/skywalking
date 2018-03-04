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

import org.apache.skywalking.apm.network.proto.RefType;
import org.apache.skywalking.apm.network.proto.TraceSegmentReference;
import org.apache.skywalking.apm.network.proto.UniqueId;

/**
 * @author peng-yongsheng
 */
public class ReferenceDecorator implements StandardBuilder {

    private boolean isOrigin = true;
    private StandardBuilder standardBuilder;
    private TraceSegmentReference referenceObject;
    private TraceSegmentReference.Builder referenceBuilder;

    public ReferenceDecorator(TraceSegmentReference referenceObject, StandardBuilder standardBuilder) {
        this.referenceObject = referenceObject;
        this.standardBuilder = standardBuilder;
    }

    public ReferenceDecorator(TraceSegmentReference.Builder referenceBuilder, StandardBuilder standardBuilder) {
        this.referenceBuilder = referenceBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
    }

    public RefType getRefType() {
        if (isOrigin) {
            return referenceObject.getRefType();
        } else {
            return referenceBuilder.getRefType();
        }
    }

    public int getRefTypeValue() {
        if (isOrigin) {
            return referenceObject.getRefTypeValue();
        } else {
            return referenceBuilder.getRefTypeValue();
        }
    }

    public int getEntryServiceId() {
        if (isOrigin) {
            return referenceObject.getEntryServiceId();
        } else {
            return referenceBuilder.getEntryServiceId();
        }
    }

    public void setEntryServiceId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setEntryServiceId(value);
    }

    public String getEntryServiceName() {
        if (isOrigin) {
            return referenceObject.getEntryServiceName();
        } else {
            return referenceBuilder.getEntryServiceName();
        }
    }

    public void setEntryServiceName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setEntryServiceName(value);
    }

    public int getEntryApplicationInstanceId() {
        if (isOrigin) {
            return referenceObject.getEntryApplicationInstanceId();
        } else {
            return referenceBuilder.getEntryApplicationInstanceId();
        }
    }

    public int getParentApplicationInstanceId() {
        if (isOrigin) {
            return referenceObject.getParentApplicationInstanceId();
        } else {
            return referenceBuilder.getParentApplicationInstanceId();
        }
    }

    public int getParentServiceId() {
        if (isOrigin) {
            return referenceObject.getParentServiceId();
        } else {
            return referenceBuilder.getParentServiceId();
        }
    }

    public void setParentServiceId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setParentServiceId(value);
    }

    public int getParentSpanId() {
        if (isOrigin) {
            return referenceObject.getParentSpanId();
        } else {
            return referenceBuilder.getParentSpanId();
        }
    }

    public String getParentServiceName() {
        if (isOrigin) {
            return referenceObject.getParentServiceName();
        } else {
            return referenceBuilder.getParentServiceName();
        }
    }

    public void setParentServiceName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setParentServiceName(value);
    }

    public UniqueId getParentTraceSegmentId() {
        if (isOrigin) {
            return referenceObject.getParentTraceSegmentId();
        } else {
            return referenceBuilder.getParentTraceSegmentId();
        }
    }

    public int getNetworkAddressId() {
        if (isOrigin) {
            return referenceObject.getNetworkAddressId();
        } else {
            return referenceBuilder.getNetworkAddressId();
        }
    }

    public void setNetworkAddressId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setNetworkAddressId(value);
    }

    public String getNetworkAddress() {
        if (isOrigin) {
            return referenceObject.getNetworkAddress();
        } else {
            return referenceBuilder.getNetworkAddress();
        }
    }

    public void setNetworkAddress(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilder.setNetworkAddress(value);
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            referenceBuilder = referenceObject.toBuilder();
            standardBuilder.toBuilder();
        }
    }
}
