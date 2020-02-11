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

package org.apache.skywalking.apm.agent.core.test.tools;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;

public class SegmentStorage {
    private LinkedList<TraceSegment> traceSegments;
    private LinkedList<IgnoredTracerContext> ignoredTracerContexts;

    public SegmentStorage() {
        traceSegments = new LinkedList<TraceSegment>();
        ignoredTracerContexts = new LinkedList<IgnoredTracerContext>();
    }

    void addTraceSegment(TraceSegment segment) {
        traceSegments.add(segment);
    }

    public List<TraceSegment> getTraceSegments() {
        return traceSegments;
    }

    void addIgnoreTraceContext(IgnoredTracerContext context) {
        this.ignoredTracerContexts.add(context);
    }

    public LinkedList<IgnoredTracerContext> getIgnoredTracerContexts() {
        return ignoredTracerContexts;
    }
}
