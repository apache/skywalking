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
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.TraceSegmentSampler;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusAnalyzer;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy;

/**
 * The agent analyzer's answers, over its configuration and the watchers {@code prepare()} set on it, the same objects
 * its own segment analysis reads.
 */
public class NativeTraceAnalysisConfigImpl implements NativeTraceAnalysisConfig {
    private final AnalyzerModuleConfig config;
    private final TraceSegmentSampler sampler;
    private final SegmentStatusAnalyzer segmentStatusAnalyzer;

    /**
     * @param config the module's configuration, with its sampling policy watcher already set
     */
    public NativeTraceAnalysisConfigImpl(final AnalyzerModuleConfig config) {
        this.config = config;
        this.sampler = new TraceSegmentSampler(config.getTraceSamplingPolicyWatcher());
        this.segmentStatusAnalyzer = SegmentStatusStrategy.findByName(config.getSegmentStatusAnalysisStrategy())
                                                          .getExceptionAnalyzer();
    }

    @Override
    public boolean isTraceAnalysis() {
        return config.isTraceAnalysis();
    }

    @Override
    public int getMaxSlowSQLLength() {
        return config.getMaxSlowSQLLength();
    }

    @Override
    public boolean isError(final SpanObject span) {
        return segmentStatusAnalyzer.isError(span);
    }

    @Override
    public boolean shouldSample(final SegmentObject segment, final int duration, final boolean isError) {
        // The policy is asked first and always, as SegmentAnalysisListener asks it.
        return sampler.shouldSample(segment, duration) || isError && config.isForceSampleErrorSegment();
    }

    @Override
    public int slowDBAccessThreshold(final String dbType) {
        return config.getDbLatencyThresholdsAndWatcher().getThreshold(dbType);
    }

    @Override
    public int slowCacheReadThreshold(final String type) {
        return config.getCacheReadLatencyThresholdsAndWatcher().getThreshold(type);
    }

    @Override
    public int slowCacheWriteThreshold(final String type) {
        return config.getCacheWriteLatencyThresholdsAndWatcher().getThreshold(type);
    }

    @Override
    public boolean isNoUpstreamRealAddressAgent(final int componentId) {
        return config.shouldIgnorePeerIPDue2Virtual(componentId);
    }
}
