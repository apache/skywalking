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

import lombok.Getter;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;

/**
 * The <code>ContextSnapshot</code> is a snapshot for current context. The snapshot carries the info for building
 * reference between two segments in two thread, but have a causal relationship.
 */
@Getter
public class ContextSnapshot {
    private DistributedTraceId traceId;
    private String traceSegmentId;
    private int spanId;
    private String parentEndpoint;

    private CorrelationContext correlationContext;
    private ExtensionContext extensionContext;

    ContextSnapshot(String traceSegmentId,
                    int spanId,
                    DistributedTraceId primaryTraceId,
                    String parentEndpoint,
                    CorrelationContext correlationContext,
                    ExtensionContext extensionContext) {
        this.traceSegmentId = traceSegmentId;
        this.spanId = spanId;
        this.traceId = primaryTraceId;
        this.parentEndpoint = parentEndpoint;
        this.correlationContext = correlationContext.clone();
        this.extensionContext = extensionContext.clone();
    }

    public boolean isFromCurrent() {
        return traceSegmentId != null && traceSegmentId.equals(ContextManager.capture().getTraceSegmentId());
    }

    public CorrelationContext getCorrelationContext() {
        return correlationContext;
    }

    public boolean isValid() {
        return traceSegmentId != null && spanId > -1 && traceId != null;
    }
}
