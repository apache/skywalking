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

import lombok.Getter;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;

/**
 * {@link TraceSegmentRef} is like a pointer, which ref to another {@link TraceSegment}, use {@link #spanId} point to
 * the exact span of the ref {@link TraceSegment}.
 * <p>
 */
@Getter
public class TraceSegmentRef {
    private SegmentRefType type;
    private String traceId;
    private String traceSegmentId;
    private int spanId;
    private String parentService;
    private String parentServiceInstance;
    private String parentEndpoint;
    private String addressUsedAtClient;

    /**
     * Transform a {@link ContextCarrier} to the <code>TraceSegmentRef</code>
     *
     * @param carrier the valid cross-process propagation format.
     */
    public TraceSegmentRef(ContextCarrier carrier) {
        this.type = SegmentRefType.CROSS_PROCESS;
        this.traceId = carrier.getTraceId();
        this.traceSegmentId = carrier.getTraceSegmentId();
        this.spanId = carrier.getSpanId();
        this.parentService = carrier.getParentService();
        this.parentServiceInstance = carrier.getParentServiceInstance();
        this.parentEndpoint = carrier.getParentEndpoint();
        this.addressUsedAtClient = carrier.getAddressUsedAtClient();
    }

    public TraceSegmentRef(ContextSnapshot snapshot) {
        this.type = SegmentRefType.CROSS_THREAD;
        this.traceId = snapshot.getTraceId().getId();
        this.traceSegmentId = snapshot.getTraceSegmentId();
        this.spanId = snapshot.getSpanId();
        this.parentService = Config.Agent.SERVICE_NAME;
        this.parentServiceInstance = Config.Agent.INSTANCE_NAME;
        this.parentEndpoint = snapshot.getParentEndpoint();
    }

    public SegmentReference transform() {
        SegmentReference.Builder refBuilder = SegmentReference.newBuilder();
        if (SegmentRefType.CROSS_PROCESS.equals(type)) {
            refBuilder.setRefType(RefType.CrossProcess);
        } else {
            refBuilder.setRefType(RefType.CrossThread);
        }
        refBuilder.setTraceId(traceId);
        refBuilder.setParentTraceSegmentId(traceSegmentId);
        refBuilder.setParentSpanId(spanId);
        refBuilder.setParentService(parentService);
        refBuilder.setParentServiceInstance(parentServiceInstance);
        refBuilder.setParentEndpoint(parentEndpoint);
        if (addressUsedAtClient != null) {
            refBuilder.setNetworkAddressUsedAtPeer(addressUsedAtClient);
        }

        return refBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TraceSegmentRef ref = (TraceSegmentRef) o;

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
        CROSS_PROCESS, CROSS_THREAD
    }
}
