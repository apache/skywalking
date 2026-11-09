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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.util.Locale;
import lombok.Builder;
import org.apache.logging.log4j.Level;
import lombok.Getter;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;

/**
 * The configuration a run uses, the same for both sides, since configuration decides branches. Every value defaults
 * to what the shipped {@code application.yml} gives without environment variables, or, for a setting the file does not
 * list, to the module configuration's own default.
 */
@Getter
@Builder(toBuilder = true)
final class CheckerProfile {
    private static final String CORE = "core";
    private static final String AGENT_ANALYZER = "agent-analyzer";
    private static final AnalyzerModuleConfig UNLISTED = new AnalyzerModuleConfig();

    private final String name;
    @Builder.Default
    private final boolean traceAnalysis = UNLISTED.isTraceAnalysis();
    @Builder.Default
    private final boolean genAiLoaded = CheckerApplicationYml.loaded("gen-ai-analyzer");
    @Builder.Default
    private final boolean evaluationLoaded = CheckerApplicationYml.loaded("ai-evaluation");
    /**
     * Whether a loaded ai-evaluation module has a complete judge configuration, which the shipped file does not give.
     */
    @Builder.Default
    private final boolean evaluationActive = false;
    /**
     * The {@code default} provider's, which a loaded ai-evaluation module uses.
     */
    @Builder.Default
    private final int evaluationSampleRate = Integer.parseInt(
        CheckerApplicationYml.setting("ai-evaluation", "default", "sampleRate"));
    @Builder.Default
    private final String slowDBAccessThreshold = CheckerApplicationYml.setting(AGENT_ANALYZER, "slowDBAccessThreshold");
    @Builder.Default
    private final String slowCacheReadThreshold = CheckerApplicationYml.setting(AGENT_ANALYZER, "slowCacheReadThreshold");
    @Builder.Default
    private final String slowCacheWriteThreshold = CheckerApplicationYml.setting(AGENT_ANALYZER, "slowCacheWriteThreshold");
    @Builder.Default
    private final int maxSlowSQLLength = UNLISTED.getMaxSlowSQLLength();
    @Builder.Default
    private final String noUpstreamRealAddressAgents = CheckerApplicationYml.setting(AGENT_ANALYZER, "noUpstreamRealAddressAgents");
    @Builder.Default
    private final String segmentStatusAnalysisStrategy = CheckerApplicationYml.setting(AGENT_ANALYZER, "segmentStatusAnalysisStrategy");
    @Builder.Default
    private final boolean forceSampleErrorSegment = Boolean.parseBoolean(CheckerApplicationYml.setting(AGENT_ANALYZER, "forceSampleErrorSegment"));
    @Builder.Default
    private final String traceSamplingPolicySettingsFile = CheckerApplicationYml.setting(AGENT_ANALYZER, "traceSamplingPolicySettingsFile");
    @Builder.Default
    private final String searchableTracesTags = CheckerApplicationYml.setting(CORE, "searchableTracesTags");
    @Builder.Default
    private final boolean endpointNameGroupingByOpenapi = Boolean.parseBoolean(CheckerApplicationYml.setting(CORE, "enableEndpointNameGroupingByOpenapi"));
    /**
     * {@code gateways.yml} content, or null for none listed.
     */
    private final String gateways;
    /**
     * {@code server-clusters.yml} content, or null for none listed.
     */
    private final String serverClusters;
    @Builder.Default
    private final int serviceNameMaxLength = Integer.parseInt(CheckerApplicationYml.setting(CORE, "serviceNameMaxLength"));
    @Builder.Default
    private final int instanceNameMaxLength = Integer.parseInt(CheckerApplicationYml.setting(CORE, "instanceNameMaxLength"));
    @Builder.Default
    private final int endpointNameMaxLength = Integer.parseInt(CheckerApplicationYml.setting(CORE, "endpointNameMaxLength"));
    @Builder.Default
    private final int maxHttpUrisNumberPerService = Integer.parseInt(
        CheckerApplicationYml.setting(CORE, "maxHttpUrisNumberPerService"));
    /**
     * {@code maxSizeOfNetworkAddressAlias}, which the shipped file does not list.
     */
    @Builder.Default
    private final long aliasCacheSize = new CoreModuleConfig().getMaxSizeOfNetworkAddressAlias();
    /**
     * Custom endpoint grouping rules, {@code endpoint-name-grouping.yml} content, or null for the shipped file.
     */
    private final String endpointGroupingRules;
    /**
     * A trace sampling policy, the policy file's content, or null for the shipped file.
     */
    private final String samplingPolicy;
    /**
     * A sampling policy both sides receive as a dynamic update halfway through each pass, or null for none.
     */
    private final String midRunSamplingPolicy;
    /**
     * Searchable tags both sides receive as a dynamic update halfway through each pass, or null for none.
     */
    private final String midRunSearchableTags;
    /**
     * The default locale while the profile runs, or null to keep the JVM's.
     */
    private final Locale locale;
    /**
     * The level the captured loggers run at, or null for TRACE, every line; a higher level takes the branches that skip
     * a disabled line.
     */
    private final Level logLevel;
    /**
     * Whether the sides carry the comparison's instrumentation: the span-index wrappers around the listeners and the
     * rules, and the recording segment-status analyzers. Off for the benchmark, which measures the engines alone.
     */
    @Builder.Default
    private final boolean instrumented = true;
    /**
     * Whether the run checks that each fixture's positive rules produced an event; on for the profiles where every
     * compiled rule is meant to fire on its positive fixtures.
     */
    private final boolean positiveGuard;

    @Override
    public String toString() {
        return name;
    }
}
