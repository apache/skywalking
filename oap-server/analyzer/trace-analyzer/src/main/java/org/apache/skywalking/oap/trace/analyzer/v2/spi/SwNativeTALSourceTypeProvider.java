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

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.ServerClusterConfig;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalConfigFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalGeneralFunctions;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.NetworkAliasMatch;

/**
 * The {@code sw-native} input: one span of a segment a SkyWalking agent sent. The rules read the wire objects the agent
 * analysis reads, read-only: {@code span} is the {@link SpanObject} itself, as an OTLP rule's {@code span} is the OTLP
 * span, and {@code segment()} its {@link SegmentObject}; the values the runner derives from the whole segment before the
 * first rule runs are read with the {@code segment...()} functions below.
 *
 * <p>A native rule reads the native span in its own words: its tags, by name with {@code tag(key)} and
 * {@code hasTag(key)} or as the {@code span.tagsList} list, are what an OTLP span's attributes are, and its logs, the
 * {@code span.logsList} list, are what its events are. It carries no resource and no instrumentation scope: the agent
 * reports its identity as the segment's service and instance, {@code segment().service} and
 * {@code segment().serviceInstance}.
 *
 * <p>Its functions are the methods below that take the run first: the tags, the naming rules, the reference and alias
 * lookups, the GenAI model match, and the agent analyzer's settings the native analysis runs under. Each calls the Java the listeners call, or reproduces a private method of it, so a
 * value, a failure and a log line are the same as theirs.
 */
public class SwNativeTALSourceTypeProvider implements TALSourceTypeProvider {
    /**
     * The services the functions read, reached once in {@link #bind}. Each is null on the default registry a unit test
     * compiles against, which binds without a module manager, and the agent analyzer's settings also when that module
     * is not loaded; a function then looks the service up through the run, as it would without them.
     */
    private NativeTraceAnalysisConfig analysisConfig;
    private ServerClusterConfig serverClusters;
    private NetworkAddressAliasCache aliasCache;

    @Override
    public String alias() {
        return TALFunctionProvider.SW_NATIVE_INPUT_TYPE;
    }

    @Override
    public Class<?> inputType() {
        return SpanObject.class;
    }

    @Override
    public Set<TALConstruct> constructs() {
        return TALConstruct.NATIVE_SEGMENT;
    }

    @Override
    public void bind(final ModuleManager moduleManager) {
        if (moduleManager == null) {
            return;
        }
        final ModuleServiceHolder core = moduleManager.find(CoreModule.NAME).provider();
        serverClusters = core.getService(ServerClusterConfig.class);
        aliasCache = core.getService(NetworkAddressAliasCache.class);
        if (moduleManager.has(AnalyzerModule.NAME)) {
            analysisConfig = moduleManager.find(AnalyzerModule.NAME).provider()
                                          .getService(NativeTraceAnalysisConfig.class);
        }
    }

    /**
     * @return the logic and configuration functions every shipped input shares
     */
    @Override
    public List<TALFunctionProvider> helperFunctions() {
        return List.of(new TalGeneralFunctions(), new TalConfigFunctions());
    }

    /**
     * {@code tag(key)}: the span's tag of that name; empty when absent. A key is not expected to repeat; when it does,
     * the first tag answers, as everywhere a native rule reads a tag by name.
     */
    public String tag(final TalRuntimeHelper h, final String key) {
        for (final KeyStringValuePair tag : span(h).getTagsList()) {
            if (tag.getKey().equals(key)) {
                return tag.getValue();
            }
        }
        return "";
    }

    /**
     * {@code hasTag(key)}: whether the span has a tag of that name.
     */
    public boolean hasTag(final TalRuntimeHelper h, final String key) {
        for (final KeyStringValuePair tag : span(h).getTagsList()) {
            if (tag.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The span's tags as {@code key.trim() + ":" + value.trim()}, in wire order, in a new list:
     * {@code EndpointSourceBuilder.setTag}'s {@code tags}.
     */
    public List<String> tagList(final TalRuntimeHelper h) {
        final List<KeyStringValuePair> tags = span(h).getTagsList();
        final List<String> list = new ArrayList<>(tags.size());
        for (final KeyStringValuePair tag : tags) {
            list.add(tag.getKey().trim() + ":" + tag.getValue().trim());
        }
        return list;
    }

    /**
     * {@code tagMap()}: the span's raw tags in a new map, the {@code originalTags} a source carries and the map the
     * GenAI and cache analyses read. A key is not expected to repeat; when it does, the first value is kept, as
     * {@link #tag} answers.
     */
    public Map<String, String> tagMap(final TalRuntimeHelper h) {
        final Map<String, String> map = new HashMap<>();
        for (final KeyStringValuePair tag : span(h).getTagsList()) {
            map.putIfAbsent(tag.getKey(), tag.getValue());
        }
        return map;
    }

    /**
     * {@code ServerClusterConfig.clusterNameOrAddress}: the cluster {@code server-clusters.yml} lists the address
     * under, or the address; nothing caught.
     */
    public String clusterNameOrAddress(final TalRuntimeHelper h, final String address) {
        final ServerClusterConfig clusters = serverClusters != null
            ? serverClusters : h.spec().service(CoreModule.NAME, ServerClusterConfig.class);
        return clusters.clusterNameOrAddress(address);
    }

    /**
     * {@code NetworkAddressAliasCache.get}, with both ids decoded as {@code RPCAnalysisListener.parseExit} decodes
     * them, the service first and both always.
     *
     * @return the alias, or null when the cache holds no row for the address
     */
    public NetworkAliasMatch findNetworkAlias(final TalRuntimeHelper h, final String address) {
        final NetworkAddressAlias alias = (aliasCache != null
            ? aliasCache : h.spec().service(CoreModule.NAME, NetworkAddressAliasCache.class)).get(address);
        return alias == null ? null : new NetworkAliasMatch(alias);
    }

    /**
     * {@code traceAnalysis()}: {@code agent-analyzer.traceAnalysis}, which the RPC, cross-thread and alias rules
     * abort on when it is false.
     */
    public boolean traceAnalysis(final TalRuntimeHelper h) {
        return analysisConfig(h).isTraceAnalysis();
    }

    /**
     * {@code maxSlowSQLLength()}: {@code agent-analyzer.maxSlowSQLLength}, the length a slow statement is cut to.
     */
    public int maxSlowSQLLength(final TalRuntimeHelper h) {
        return analysisConfig(h).getMaxSlowSQLLength();
    }

    /**
     * {@code slowDBAccessThreshold(type)}: the slow statement threshold of the database type, in milliseconds, as
     * {@code agent-analyzer.slowDBAccessThreshold} sets it, read at the call.
     */
    public int slowDBAccessThreshold(final TalRuntimeHelper h, final String dbType) {
        return analysisConfig(h).slowDBAccessThreshold(dbType);
    }

    /**
     * {@code slowCacheReadThreshold(type)}: the slow read threshold of the cache type, in milliseconds, read at the
     * call.
     */
    public int slowCacheReadThreshold(final TalRuntimeHelper h, final String type) {
        return analysisConfig(h).slowCacheReadThreshold(type);
    }

    /**
     * {@code slowCacheWriteThreshold(type)}: the slow write threshold of the cache type, in milliseconds, read at the
     * call.
     */
    public int slowCacheWriteThreshold(final TalRuntimeHelper h, final String type) {
        return analysisConfig(h).slowCacheWriteThreshold(type);
    }

    /**
     * {@code segment()}: the segment the span belongs to, as the agent sent it.
     */
    public SegmentObject segment(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().getSegmentObject();
    }

    /**
     * {@code segmentStartTime()}: the segment's start, the fold over its spans' starts.
     */
    public long segmentStartTime(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().getStartTime();
    }

    /**
     * {@code segmentLatency()}: the segment's duration, capped at {@code Integer.MAX_VALUE}.
     */
    public int segmentLatency(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().getLatency();
    }

    /**
     * {@code segmentIsError()}: whether the segment is an error under the configured segment status strategy.
     */
    public boolean segmentIsError(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().isError();
    }

    /**
     * {@code segmentTags()}: the searchable tags the segment record stores.
     */
    public List<Tag> segmentTags(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().getTags();
    }

    /**
     * {@code segmentSampled()}: whether the segment is kept, the sampling decision or a forced error segment.
     */
    public boolean segmentSampled(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().isSampled();
    }

    /**
     * {@code segmentBytes()}: the segment serialized again, what the segment record stores; serialized at each call.
     */
    public byte[] segmentBytes(final TalRuntimeHelper h) {
        return h.ctx().getNativeSegment().getSegmentObject().toByteArray();
    }

    private NativeTraceAnalysisConfig analysisConfig(final TalRuntimeHelper h) {
        final NativeTraceAnalysisConfig bound = analysisConfig;
        return bound != null ? bound : h.spec().service(AnalyzerModule.NAME, NativeTraceAnalysisConfig.class);
    }

    private static SpanObject span(final TalRuntimeHelper h) {
        return (SpanObject) h.input();
    }
}
