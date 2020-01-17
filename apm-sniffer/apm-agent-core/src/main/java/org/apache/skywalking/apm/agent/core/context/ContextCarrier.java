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

package org.apache.skywalking.apm.agent.core.context;

import java.io.Serializable;
import java.util.List;
import org.apache.skywalking.apm.agent.core.base64.Base64;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link ContextCarrier} is a data carrier of {@link TracingContext}. It holds the snapshot (current state) of {@link
 * TracingContext}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class ContextCarrier implements Serializable {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    private ID traceSegmentId;

    /**
     * id of parent span. It is unique in parent trace segment.
     */
    private int spanId = -1;

    /**
     * id of parent application instance, it's the id assigned by collector.
     */
    private int parentServiceInstanceId = DictionaryUtil.nullValue();

    /**
     * id of first application instance in this distributed trace, it's the id assigned by collector.
     */
    private int entryServiceInstanceId = DictionaryUtil.nullValue();

    /**
     * peer(ipv4s/ipv6/hostname + port) of the server, from client side.
     */
    private String peerHost;

    /**
     * Operation/Service name of the first one in this distributed trace. This name may be compressed to an integer.
     */
    private String entryEndpointName;

    /**
     * Operation/Service name of the parent one in this distributed trace. This name may be compressed to an integer.
     */
    private String parentEndpointName;

    /**
     * {@link DistributedTraceId}, also known as TraceId
     */
    private DistributedTraceId primaryDistributedTraceId;

    public CarrierItem items() {
        SW6CarrierItem sw6CarrierItem = new SW6CarrierItem(this, null);
        return new CarrierItemHead(sw6CarrierItem);
    }

    /**
     * Serialize this {@link ContextCarrier} to a {@link String}, with '|' split.
     *
     * @return the serialization string.
     */
    String serialize(HeaderVersion version) {
        if (this.isValid(version)) {
            return StringUtil.join('-',
                "1",
                Base64.encode(this.getPrimaryDistributedTraceId().encode()),
                Base64.encode(this.getTraceSegmentId().encode()),
                this.getSpanId() + "",
                this.getParentServiceInstanceId() + "",
                this.getEntryServiceInstanceId() + "",
                Base64.encode(this.getPeerHost()),
                Base64.encode(this.getEntryEndpointName()),
                Base64.encode(this.getParentEndpointName()));
        } else {
            return "";
        }
    }

    /**
     * Initialize fields with the given text.
     *
     * @param text carries {@link #traceSegmentId} and {@link #spanId}, with '|' split.
     */
    ContextCarrier deserialize(String text, HeaderVersion version) {
        if (text != null) {
            // if this carrier is initialized by v1 or v2, don't do deserialize again for performance.
            if (this.isValid(HeaderVersion.v1) || this.isValid(HeaderVersion.v2)) {
                return this;
            }
            if (HeaderVersion.v1.equals(version)) {
                String[] parts = text.split("\\|", 8);
                if (parts.length == 8) {
                    try {
                        this.traceSegmentId = new ID(parts[0]);
                        this.spanId = Integer.parseInt(parts[1]);
                        this.parentServiceInstanceId = Integer.parseInt(parts[2]);
                        this.entryServiceInstanceId = Integer.parseInt(parts[3]);
                        this.peerHost = parts[4];
                        this.entryEndpointName = parts[5];
                        this.parentEndpointName = parts[6];
                        this.primaryDistributedTraceId = new PropagatedTraceId(parts[7]);
                    } catch (NumberFormatException e) {

                    }
                }
            } else if (HeaderVersion.v2.equals(version)) {
                String[] parts = text.split("\\-", 9);
                if (parts.length == 9) {
                    try {
                        // parts[0] is sample flag, always trace if header exists.
                        this.primaryDistributedTraceId = new PropagatedTraceId(Base64.decode2UTFString(parts[1]));
                        this.traceSegmentId = new ID(Base64.decode2UTFString(parts[2]));
                        this.spanId = Integer.parseInt(parts[3]);
                        this.parentServiceInstanceId = Integer.parseInt(parts[4]);
                        this.entryServiceInstanceId = Integer.parseInt(parts[5]);
                        this.peerHost = Base64.decode2UTFString(parts[6]);
                        this.entryEndpointName = Base64.decode2UTFString(parts[7]);
                        this.parentEndpointName = Base64.decode2UTFString(parts[8]);
                    } catch (NumberFormatException e) {

                    }
                }
            } else {
                throw new IllegalArgumentException("Unimplemented header version." + version);
            }
        }
        return this;
    }

    public boolean isValid() {
        return isValid(HeaderVersion.v2) || isValid(HeaderVersion.v1);
    }

    /**
     * Make sure this {@link ContextCarrier} has been initialized.
     *
     * @return true for unbroken {@link ContextCarrier} or no-initialized. Otherwise, false;
     */
    boolean isValid(HeaderVersion version) {
        if (HeaderVersion.v1.equals(version)) {
            return traceSegmentId != null
                && traceSegmentId.isValid()
                && getSpanId() > -1
                && parentServiceInstanceId != DictionaryUtil.nullValue()
                && entryServiceInstanceId != DictionaryUtil.nullValue()
                && !StringUtil.isEmpty(peerHost)
                && !StringUtil.isEmpty(entryEndpointName)
                && !StringUtil.isEmpty(parentEndpointName)
                && primaryDistributedTraceId != null;
        } else if (HeaderVersion.v2.equals(version)) {
            return traceSegmentId != null
                && traceSegmentId.isValid()
                && getSpanId() > -1
                && parentServiceInstanceId != DictionaryUtil.nullValue()
                && entryServiceInstanceId != DictionaryUtil.nullValue()
                && !StringUtil.isEmpty(peerHost)
                && primaryDistributedTraceId != null;
        } else {
            throw new IllegalArgumentException("Unimplemented header version." + version);
        }
    }

    public String getEntryEndpointName() {
        return entryEndpointName;
    }

    void setEntryEndpointName(String entryEndpointName) {
        this.entryEndpointName = '#' + entryEndpointName;
    }

    void setEntryEndpointId(int entryOperationId) {
        this.entryEndpointName = entryOperationId + "";
    }

    void setParentEndpointName(String parentEndpointName) {
        this.parentEndpointName = '#' + parentEndpointName;
    }

    void setParentEndpointId(int parentOperationId) {
        this.parentEndpointName = parentOperationId + "";
    }

    public ID getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    void setTraceSegmentId(ID traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    void setSpanId(int spanId) {
        this.spanId = spanId;
    }

    public int getParentServiceInstanceId() {
        return parentServiceInstanceId;
    }

    void setParentServiceInstanceId(int parentServiceInstanceId) {
        this.parentServiceInstanceId = parentServiceInstanceId;
    }

    public String getPeerHost() {
        return peerHost;
    }

    void setPeerHost(String peerHost) {
        this.peerHost = '#' + peerHost;
    }

    void setPeerId(int peerId) {
        this.peerHost = peerId + "";
    }

    public DistributedTraceId getDistributedTraceId() {
        return primaryDistributedTraceId;
    }

    public void setDistributedTraceIds(List<DistributedTraceId> distributedTraceIds) {
        this.primaryDistributedTraceId = distributedTraceIds.get(0);
    }

    private DistributedTraceId getPrimaryDistributedTraceId() {
        return primaryDistributedTraceId;
    }

    public String getParentEndpointName() {
        return parentEndpointName;
    }

    public int getEntryServiceInstanceId() {
        return entryServiceInstanceId;
    }

    public void setEntryServiceInstanceId(int entryServiceInstanceId) {
        this.entryServiceInstanceId = entryServiceInstanceId;
    }

    public enum HeaderVersion {
        v1, v2
    }
}
