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

import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * The <code>StackBasedTracingSpan</code> represents a span with an inside stack construction.
 * <p>
 * This kind of span can start and finish multi times in a stack-like invoke line.
 */
public abstract class StackBasedTracingSpan extends AbstractTracingSpan {
    protected int stackDepth;
    protected String peer;

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.stackDepth = 0;
        this.peer = null;
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, String peer,
                                    TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.peer = peer;
    }

    @Override
    public SpanObject.Builder transform() {
        SpanObject.Builder spanBuilder = super.transform();
        if (StringUtil.isNotEmpty(peer)) {
            spanBuilder.setPeer(peer);
        }
        return spanBuilder;
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            return super.finish(owner);
        } else {
            return false;
        }
    }

    @Override
    public AbstractSpan setPeer(final String remotePeer) {
        this.peer = remotePeer;
        return this;
    }
}
