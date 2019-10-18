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
import org.apache.skywalking.apm.network.language.agent.RefType;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentReference;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment}, use {@link #spanId} point to
 * the exact span of the ref {@link TraceSegment}.
 * <p>
 * Created by wusheng on 2017/2/17.
 */
public class TraceSegmentRef {
    private SegmentRefType type;

    private ID traceSegmentId;

    private int spanId = -1;

    private int peerId = DictionaryUtil.nullValue();

    private String peerHost;

    private int entryServiceInstanceId = DictionaryUtil.nullValue();

    private int parentServiceInstanceId = DictionaryUtil.nullValue();

    private String entryEndpointName;

    private int entryEndpointId = DictionaryUtil.nullValue();

    private String parentEndpointName;

    private int parentEndpointId = DictionaryUtil.nullValue();

    /**
     * Transform a {@link ContextCarrier} to the <code>TraceSegmentRef</code>
     *
     * @param carrier the valid cross-process propagation format.
     */
    public TraceSegmentRef(ContextCarrier carrier) {
        this.type = SegmentRefType.CROSS_PROCESS;
        this.traceSegmentId = carrier.getTraceSegmentId();
        this.spanId = carrier.getSpanId();
        this.parentServiceInstanceId = carrier.getParentServiceInstanceId();
        this.entryServiceInstanceId = carrier.getEntryServiceInstanceId();
        String host = carrier.getPeerHost();
        if (host.charAt(0) == '#') {
            this.peerHost = host.substring(1);
        } else {
            this.peerId = Integer.parseInt(host);
        }
        String entryOperationName = carrier.getEntryEndpointName();
        if (!StringUtil.isEmpty(entryOperationName)) {
            if (entryOperationName.charAt(0) == '#') {
                this.entryEndpointName = entryOperationName.substring(1);
            } else {
                this.entryEndpointId = Integer.parseInt(entryOperationName);
            }
        }
        String parentOperationName = carrier.getParentEndpointName();
        if (!StringUtil.isEmpty(parentOperationName)) {
            if (parentOperationName.charAt(0) == '#') {
                this.parentEndpointName = parentOperationName.substring(1);
            } else {
                this.parentEndpointId = Integer.parseInt(parentOperationName);
            }
        }
    }

    public TraceSegmentRef(ContextSnapshot snapshot) {
        this.type = SegmentRefType.CROSS_THREAD;
        this.traceSegmentId = snapshot.getTraceSegmentId();
        this.spanId = snapshot.getSpanId();
        this.parentServiceInstanceId = RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID;
        this.entryServiceInstanceId = snapshot.getEntryApplicationInstanceId();
        String entryOperationName = snapshot.getEntryOperationName();
        if (!StringUtil.isEmpty(entryOperationName)) {
            if (entryOperationName.charAt(0) == '#') {
                this.entryEndpointName = entryOperationName.substring(1);
            } else {
                this.entryEndpointId = Integer.parseInt(entryOperationName);
            }
        }
        String parentOperationName = snapshot.getParentOperationName();
        if (!StringUtil.isEmpty(parentOperationName)) {
            if (parentOperationName.charAt(0) == '#') {
                this.parentEndpointName = parentOperationName.substring(1);
            } else {
                this.parentEndpointId = Integer.parseInt(parentOperationName);
            }
        }
    }

    public String getEntryEndpointName() {
        return entryEndpointName;
    }

    public int getEntryEndpointId() {
        return entryEndpointId;
    }

    public int getEntryServiceInstanceId() {
        return entryServiceInstanceId;
    }

    public SegmentReference transform() {
        SegmentReference.Builder refBuilder = SegmentReference.newBuilder();
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

        refBuilder.setParentServiceInstanceId(parentServiceInstanceId);
        refBuilder.setEntryServiceInstanceId(entryServiceInstanceId);
        refBuilder.setParentTraceSegmentId(traceSegmentId.transform());
        refBuilder.setParentSpanId(spanId);
        /**
         * entryEndpointId/entryEndpointName and parentEndpointId/parentEndpointName could be empty at same time.
         * This is accepted in v2 format.
         *
         */
        if (entryEndpointId == DictionaryUtil.nullValue()) {
            if (!StringUtil.isEmpty(entryEndpointName)) {
                refBuilder.setEntryEndpoint(entryEndpointName);
            }
        } else {
            refBuilder.setEntryEndpointId(entryEndpointId);
        }
        if (parentEndpointId == DictionaryUtil.nullValue()) {
            if (!StringUtil.isEmpty(parentEndpointName)) {
                refBuilder.setParentEndpoint(parentEndpointName);
            }
        } else {
            refBuilder.setParentEndpointId(parentEndpointId);
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
