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

package org.apache.skywalking.oap.server.core.query.type.debugging;

import java.util.Stack;
import lombok.Getter;

@Getter
public class DebuggingTraceContext {
    public final static ThreadLocal<DebuggingTraceContext> TRACE_CONTEXT = new ThreadLocal<>();
    private final DebuggingTrace execTrace;
    private final Stack<DebuggingSpan> spanStack = new Stack<>();
    private int spanIdGenerator;
    private final boolean debug;
    private final boolean dumpStorageRsp;

    public DebuggingTraceContext(String condition, boolean debug, boolean dumpStorageRsp) {
        this.execTrace = new DebuggingTrace(condition);
        this.debug = debug;
        this.dumpStorageRsp = dumpStorageRsp;
    }

    public DebuggingSpan createSpan(String operation) {
        DebuggingSpan span = new DebuggingSpan(spanIdGenerator++, operation);
        if (debug) {
            DebuggingSpan parentSpan = spanStack.isEmpty() ? null : spanStack.peek();
            if (parentSpan != null) {
                span.setParentSpanId(parentSpan.getSpanId());
            } else {
                span.setParentSpanId(-1);
            }
            spanStack.push(span);
            execTrace.addSpan(span);
        }
        return span;
    }

    public void stopSpan(DebuggingSpan span) {
        if (debug) {
            span.stopSpan();
            if (spanStack.isEmpty()) {
                return;
            }
            spanStack.pop();
        }
    }

    public void stopTrace() {
        if (debug) {
            execTrace.stopTrace();
        }
    }
}
