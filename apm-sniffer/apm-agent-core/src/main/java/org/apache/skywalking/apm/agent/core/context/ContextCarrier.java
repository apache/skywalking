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
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link ContextCarrier} is a data carrier of {@link TracingContext}.
 * It holds the snapshot (current state) of {@link TracingContext}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class ContextCarrier implements Serializable {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    private ID traceSegmentId;

    /**
     * id of parent span.
     * It is unique in parent trace segment.
     */
    private int spanId = -1;

    /**
     * id of parent application instance, it's the id assigned by collector.
     */
    private int parentApplicationInstanceId = DictionaryUtil.nullValue();

    /**
     * id of first application instance in this distributed trace, it's the id assigned by collector.
     */
    private int entryApplicationInstanceId = DictionaryUtil.nullValue();

    /**
     * peer(ipv4/ipv6/hostname + port) of the server, from client side.
     */
    private String peerHost;

    /**
     * Operation/Service name of the first one in this distributed trace.
     * This name may be compressed to an integer.
     */
    private String entryOperationName;

    /**
     * Operation/Service name of the parent one in this distributed trace.
     * This name may be compressed to an integer.
     */
    private String parentOperationName;

    /**
     * {@link DistributedTraceId}, also known as TraceId
     */
    private DistributedTraceId primaryDistributedTraceId;

    public CarrierItem items() {
        SW3CarrierItem carrierItem = new SW3CarrierItem(this, null);
        CarrierItemHead head = new CarrierItemHead(carrierItem);
        return head;
    }

    /**
     * Serialize this {@link ContextCarrier} to a {@link String},
     * with '|' split.
     *
     * @return the serialization string.
     */
    String serialize() {
        if (this.isValid()) {
            return StringUtil.join('|',
                this.getTraceSegmentId().encode(),
                this.getSpanId() + "",
                this.getParentApplicationInstanceId() + "",
                this.getEntryApplicationInstanceId() + "",
                this.getPeerHost(),
                this.getEntryOperationName(),
                this.getParentOperationName(),
                this.getPrimaryDistributedTraceId().encode());
        } else {
            return "";
        }
    }

    /**
     * Initialize fields with the given text.
     *
     * @param text carries {@link #traceSegmentId} and {@link #spanId}, with '|' split.
     */
    ContextCarrier deserialize(String text) {
        if (text != null) {
            String[] parts = text.split("\\|", 8);
            if (parts.length == 8) {
                try {
                    this.traceSegmentId = new ID(parts[0]);
                    this.spanId = Integer.parseInt(parts[1]);
                    this.parentApplicationInstanceId = Integer.parseInt(parts[2]);
                    this.entryApplicationInstanceId = Integer.parseInt(parts[3]);
                    this.peerHost = parts[4];
                    this.entryOperationName = parts[5];
                    this.parentOperationName = parts[6];
                    this.primaryDistributedTraceId = new PropagatedTraceId(parts[7]);
                } catch (NumberFormatException e) {

                }
            }
        }
        return this;
    }

    /**
     * Make sure this {@link ContextCarrier} has been initialized.
     *
     * @return true for unbroken {@link ContextCarrier} or no-initialized. Otherwise, false;
     */
    public boolean isValid() {
        return traceSegmentId != null
            && traceSegmentId.isValid()
            && getSpanId() > -1
            && parentApplicationInstanceId != DictionaryUtil.nullValue()
            && entryApplicationInstanceId != DictionaryUtil.nullValue()
            && !StringUtil.isEmpty(peerHost)
            && !StringUtil.isEmpty(entryOperationName)
            && !StringUtil.isEmpty(parentOperationName)
            && primaryDistributedTraceId != null;
    }

    public String getEntryOperationName() {
        return entryOperationName;
    }

    void setEntryOperationName(String entryOperationName) {
        this.entryOperationName = '#' + entryOperationName;
    }

    void setEntryOperationId(int entryOperationId) {
        this.entryOperationName = entryOperationId + "";
    }

    void setParentOperationName(String parentOperationName) {
        this.parentOperationName = '#' + parentOperationName;
    }

    void setParentOperationId(int parentOperationId) {
        this.parentOperationName = parentOperationId + "";
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

    public int getParentApplicationInstanceId() {
        return parentApplicationInstanceId;
    }

    void setParentApplicationInstanceId(int parentApplicationInstanceId) {
        this.parentApplicationInstanceId = parentApplicationInstanceId;
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

    public String getParentOperationName() {
        return parentOperationName;
    }

    public int getEntryApplicationInstanceId() {
        return entryApplicationInstanceId;
    }

    public void setEntryApplicationInstanceId(int entryApplicationInstanceId) {
        this.entryApplicationInstanceId = entryApplicationInstanceId;
    }

}
