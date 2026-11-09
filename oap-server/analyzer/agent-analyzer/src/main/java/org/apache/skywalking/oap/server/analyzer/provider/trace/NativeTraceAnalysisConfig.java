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
 */

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * What the agent analyzer's settings decide about the native trace analysis, for the trace analyzer, which runs it as
 * TAL rules. Dynamic settings are read at the call, so a value changed later reads its new value.
 */
public interface NativeTraceAnalysisConfig extends Service {
    /**
     * @return {@code agent-analyzer.traceAnalysis}
     */
    boolean isTraceAnalysis();

    /**
     * @return {@code agent-analyzer.maxSlowSQLLength}
     */
    int getMaxSlowSQLLength();

    /**
     * @param span a span of the segment
     * @return whether the span makes its segment an error, under {@code agent-analyzer.segmentStatusAnalysisStrategy}
     */
    boolean isError(SpanObject span);

    /**
     * @param segment  the segment
     * @param duration the segment's duration, capped at {@code Integer.MAX_VALUE}
     * @param isError  whether the segment is an error
     * @return whether the segment is sampled: by the trace sampling policy, or as an error segment under
     * {@code agent-analyzer.forceSampleErrorSegment}
     */
    boolean shouldSample(SegmentObject segment, int duration, boolean isError);

    /**
     * @param dbType the database type
     * @return the slow statement threshold of the type, in milliseconds
     */
    int slowDBAccessThreshold(String dbType);

    /**
     * @param type the cache type
     * @return the slow read threshold of the type, in milliseconds
     */
    int slowCacheReadThreshold(String type);

    /**
     * @param type the cache type
     * @return the slow write threshold of the type, in milliseconds
     */
    int slowCacheWriteThreshold(String type);

    /**
     * @param componentId the component of an exit span
     * @return whether {@code agent-analyzer.noUpstreamRealAddressAgents} lists the component, whose peer is not the
     * real address of the service it calls
     */
    boolean isNoUpstreamRealAddressAgent(int componentId);
}
