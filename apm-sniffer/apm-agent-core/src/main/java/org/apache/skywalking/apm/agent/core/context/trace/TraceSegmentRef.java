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


package org.apache.skywalking.apm.agent.core.context.trace;

import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.network.proto.RefType;
import org.apache.skywalking.apm.network.proto.TraceSegmentReference;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment},
 * use {@link #spanId} point to the exact span of the ref {@link TraceSegment}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentRef {
    private SegmentRefType type;

    private ID traceSegmentId;

    private int spanId = -1;

    private int peerId = DictionaryUtil.nullValue();

    private String peerHost;

    private int entryApplicationInstanceId = DictionaryUtil.nullValue();

    private int parentApplicationInstanceId = DictionaryUtil.nullValue();

    private String entryOperationName;

    private int entryOperationId = DictionaryUtil.nullValue();

    private String parentOperationName;

    private int parentOperationId = DictionaryUtil.nullValue();

    /**
     * Transform a {@link ContextCarrier} to the <code>TraceSegmentRef</code>
     *
     * @param carrier the valid cross-process propagation format.
     */
    public TraceSegmentRef(ContextCarrier carrier) {
        this.type = SegmentRefType.CROSS_PROCESS;
        this.traceSegmentId = carrier.getTraceSegmentId();
        this.spanId = carrier.getSpanId();
        this.parentApplicationInstanceId = carrier.getParentApplicationInstanceId();
        this.entryApplicationInstanceId = carrier.getEntryApplicationInstanceId();
        String host = carrier.getPeerHost();
        if (host.charAt(0) == '#') {
            this.peerHost = host.substring(1);
        } else {
            this.peerId = Integer.parseInt(host);
        }
        String entryOperationName = carrier.getEntryOperationName();
        if (entryOperationName.charAt(0) == '#') {
            this.entryOperationName = entryOperationName.substring(1);
        } else {
            this.entryOperationId = Integer.parseInt(entryOperationName);
        }
        String parentOperationName = carrier.getParentOperationName();
        if (parentOperationName.charAt(0) == '#') {
            this.parentOperationName = parentOperationName.substring(1);
        } else {
            this.parentOperationId = Integer.parseInt(parentOperationName);
        }
    }

    public TraceSegmentRef(ContextSnapshot snapshot) {
        this.type = SegmentRefType.CROSS_THREAD;
        this.traceSegmentId = snapshot.getTraceSegmentId();
        this.spanId = snapshot.getSpanId();
        this.parentApplicationInstanceId = RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID;
        this.entryApplicationInstanceId = snapshot.getEntryApplicationInstanceId();
        String entryOperationName = snapshot.getEntryOperationName();
        if (entryOperationName.charAt(0) == '#') {
            this.entryOperationName = entryOperationName.substring(1);
        } else {
            this.entryOperationId = Integer.parseInt(entryOperationName);
        }
        String parentOperationName = snapshot.getParentOperationName();
        if (parentOperationName.charAt(0) == '#') {
            this.parentOperationName = parentOperationName.substring(1);
        } else {
            this.parentOperationId = Integer.parseInt(parentOperationName);
        }
    }

    public String getEntryOperationName() {
        return entryOperationName;
    }

    public int getEntryOperationId() {
        return entryOperationId;
    }

    public int getEntryApplicationInstanceId() {
        return entryApplicationInstanceId;
    }

    public TraceSegmentReference transform() {
        TraceSegmentReference.Builder refBuilder = TraceSegmentReference.newBuilder();
        if (SegmentRefType.CROSS_PROCESS.equals(type)) {
            refBuilder.setRefType(RefType.CrossProcess);
            if (peerId == DictionaryUtil.nullValue()) {
                refBuilder.setNetworkAddress(peerHost);
            } else {
                refBuilder.setNetworkAddressId(peerId);
            }
        } else {
            refBuilder.setRefType(RefType.CrossThread);
        }

        refBuilder.setParentApplicationInstanceId(parentApplicationInstanceId);
        refBuilder.setEntryApplicationInstanceId(entryApplicationInstanceId);
        refBuilder.setParentTraceSegmentId(traceSegmentId.transform());
        refBuilder.setParentSpanId(spanId);
        if (entryOperationId == DictionaryUtil.nullValue()) {
            refBuilder.setEntryServiceName(entryOperationName);
        } else {
            refBuilder.setEntryServiceId(entryOperationId);
        }
        if (parentOperationId == DictionaryUtil.nullValue()) {
            refBuilder.setParentServiceName(parentOperationName);
        } else {
            refBuilder.setParentServiceId(parentOperationId);
        }
        return refBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TraceSegmentRef ref = (TraceSegmentRef)o;

        if (spanId != ref.spanId)
            return false;
        return traceSegmentId.equals(ref.traceSegmentId);
    }

    @Override
    public int hashCode() {
        int result = traceSegmentId.hashCode();
        result = 31 * result + spanId;
        return result;
    }

    public enum SegmentRefType {
        CROSS_PROCESS,
        CROSS_THREAD
    }
}
