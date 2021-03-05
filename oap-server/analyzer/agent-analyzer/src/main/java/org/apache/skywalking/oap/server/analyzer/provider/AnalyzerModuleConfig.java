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

package org.apache.skywalking.oap.server.analyzer.provider;

import com.google.common.base.Splitter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSampleRateWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

import java.util.ArrayList;
import java.util.List;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy.FROM_SPAN_STATUS;

@Slf4j
public class AnalyzerModuleConfig extends ModuleConfig {
    /**
     * The sample rate precision is 1/10000. 10000 means 100% sample in default.
     */
    @Setter
    @Getter
    private int sampleRate = 10000;
    /**
     * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
     * service instance mapping and service instance client side relation are ignored.
     *
     * Read component-libraries.yml for more details.
     */
    @Getter
    private String noUpstreamRealAddressAgents = Const.EMPTY_STRING;
    /**
     * The threshold used to check the slow database access. Unit, millisecond.
     */
    @Setter
    @Getter
    private String slowDBAccessThreshold = "default:200";
    /**
     * Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond.
     */
    @Setter
    @Getter
    private int slowTraceSegmentThreshold = -1;
    @Setter
    @Getter
    private DBLatencyThresholdsAndWatcher dbLatencyThresholdsAndWatcher;
    @Setter
    @Getter
    private UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig;
    @Setter
    @Getter
    private TraceSampleRateWatcher traceSampleRateWatcher;
    @Setter
    @Getter
    private TraceLatencyThresholdsAndWatcher traceLatencyThresholdsAndWatcher;
    /**
     * Analysis trace status.
     * <p>
     * 1. Default(YES) means analysis all metrics from trace.
     * <p>
     * 2. NO means, only save trace, but metrics come other places, such as service mesh.
     */
    @Setter
    @Getter
    private boolean traceAnalysis = true;
    /**
     * Slow Sql string length can't beyond this limit. This value should be as same as the length annotation at the
     * {@code org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement#statement}. And share
     * the system env name, SW_SLOW_DB_THRESHOLD
     */
    @Setter
    @Getter
    private int maxSlowSQLLength = 2000;

    @Getter
    private final String configPath = "meter-analyzer-config";

    /**
     * Which files could be meter analyzed, files split by ","
     */
    @Setter
    private String meterAnalyzerActiveFiles = Const.EMPTY_STRING;

    /**
     * Sample the trace segment if the segment has span(s) tagged as error status, and ignore the sampleRate
     * configuration.
     */
    @Setter
    @Getter
    private boolean forceSampleErrorSegment = true;

    /**
     * Determine the final segment status from the status of spans.
     *
     * @see SegmentStatusStrategy
     */
    @Setter
    @Getter
    private String segmentStatusAnalysisStrategy = FROM_SPAN_STATUS.name();

    private List<Integer> virtualPeers;

    /**
     * @param componentId of the exit span
     * @return true, means should not generate the instance relationship for the client-side exit span.
     */
    public boolean shouldIgnorePeerIPDue2Virtual(int componentId) {
        if (virtualPeers == null) {
            virtualPeers = new ArrayList<>(20);
            for (final String component : noUpstreamRealAddressAgents.split(",")) {
                try {
                    virtualPeers.add(Integer.parseInt(component));
                } catch (NumberFormatException e) {
                    log.warn("noUpstreamRealAddressAgents config {} includes illegal value {}",
                             noUpstreamRealAddressAgents, component
                    );
                }
            }
        }
        return virtualPeers.contains(componentId);
    }

    /**
     * Get all files could be meter analyzed, files split by ","
     */
    public List<String> meterAnalyzerActiveFileNames() {
        if (StringUtils.isEmpty(this.meterAnalyzerActiveFiles)) {
            return null;
        }
        return Splitter.on(",").splitToList(this.meterAnalyzerActiveFiles);
    }
}
