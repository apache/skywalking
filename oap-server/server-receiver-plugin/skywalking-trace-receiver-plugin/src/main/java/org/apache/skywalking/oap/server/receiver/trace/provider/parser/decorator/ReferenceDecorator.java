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

import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;

/**
 * @author peng-yongsheng
 */
public class ReferenceDecorator implements StandardBuilder {

    private boolean isOrigin = true;
    private final StandardBuilder standardBuilder;
    private SegmentReference referenceObjectV2;
    private SegmentReference.Builder referenceBuilderV2;

    public ReferenceDecorator(SegmentReference referenceObject, StandardBuilder standardBuilder) {
        this.referenceObjectV2 = referenceObject;
        this.standardBuilder = standardBuilder;
    }

    public ReferenceDecorator(SegmentReference.Builder referenceBuilder, StandardBuilder standardBuilder) {
        this.referenceBuilderV2 = referenceBuilder;
        this.standardBuilder = standardBuilder;
        this.isOrigin = false;
    }

    public int getEntryEndpointId() {
        if (isOrigin) {
            return referenceObjectV2.getEntryEndpointId();
        } else {
            return referenceBuilderV2.getEntryEndpointId();
        }
    }

    public void setEntryEndpointId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setEntryEndpointId(value);
    }

    public String getEntryEndpointName() {
        if (isOrigin) {
            return referenceObjectV2.getEntryEndpoint();
        } else {
            return referenceBuilderV2.getEntryEndpoint();
        }
    }

    public void setEntryEndpointName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setEntryEndpoint(value);
    }

    public int getEntryServiceInstanceId() {
        if (isOrigin) {
            return referenceObjectV2.getEntryServiceInstanceId();
        } else {
            return referenceBuilderV2.getEntryServiceInstanceId();
        }
    }

    public int getParentServiceInstanceId() {
        if (isOrigin) {
            return referenceObjectV2.getParentServiceInstanceId();
        } else {
            return referenceBuilderV2.getParentServiceInstanceId();
        }
    }

    public int getParentEndpointId() {
        if (isOrigin) {
            return referenceObjectV2.getParentEndpointId();
        } else {
            return referenceBuilderV2.getParentEndpointId();
        }
    }

    public void setParentEndpointId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setParentEndpointId(value);
    }

    public String getParentEndpointName() {
        if (isOrigin) {
            return referenceObjectV2.getParentEndpoint();
        } else {
            return referenceBuilderV2.getParentEndpoint();
        }
    }

    public void setParentEndpointName(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setParentEndpoint(value);
    }

    public int getNetworkAddressId() {
        if (isOrigin) {
            return referenceObjectV2.getNetworkAddressId();
        } else {
            return referenceBuilderV2.getNetworkAddressId();
        }
    }

    public void setNetworkAddressId(int value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setNetworkAddressId(value);
    }

    public String getNetworkAddress() {
        if (isOrigin) {
            return referenceObjectV2.getNetworkAddress();
        } else {
            return referenceBuilderV2.getNetworkAddress();
        }
    }

    public void setNetworkAddress(String value) {
        if (isOrigin) {
            toBuilder();
        }
        referenceBuilderV2.setNetworkAddress(value);
    }

    @Override public void toBuilder() {
        if (this.isOrigin) {
            this.isOrigin = false;
            referenceBuilderV2 = referenceObjectV2.toBuilder();
            standardBuilder.toBuilder();
        }
    }
}
