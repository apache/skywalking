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

package org.apache.skywalking.apm.agent.test.tools;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;

public class SegmentStorage {
    private List<TraceSegment> traceSegments;
    private List<IgnoredTracerContext> ignoredTracerContexts;

    public SegmentStorage() {
        traceSegments = new ArrayList<TraceSegment>();
        ignoredTracerContexts = new ArrayList<IgnoredTracerContext>();
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

    public List<IgnoredTracerContext> getIgnoredTracerContexts() {
        return ignoredTracerContexts;
    }
}
