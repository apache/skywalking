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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.QueuedBuild;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * Runs the native rules over one segment a SkyWalking agent sent, callback for callback as {@code TraceAnalyzer} runs
 * the agent analysis's listeners:
 * <ol>
 *     <li>a segment without spans is left alone;</li>
 *     <li>{@code SegmentAnalysisListener.parseSegment}'s work, before any rule runs, read with the segment functions
 *     ({@code segmentStartTime()}, {@code segmentLatency()} and the others);</li>
 *     <li>for each span in wire order: on a span whose {@code spanId} is {@code 0}, the {@code first {}} run of every
 *     rule, in rule order; then the regular run of every rule, in rule order, whatever the span's type, after the
 *     error {@code TraceAnalyzer} logs for a type it does not know;</li>
 *     <li>after the last span, the queued {@code build {}} blocks, rule by rule in rule order, and within a rule in
 *     the order they were queued.</li>
 * </ol>
 *
 * <p>A run's queued blocks are kept only when the run was not aborted. Each segment has its own {@code segmentDef}
 * values per rule, gone when the segment ends. Nothing is caught: a rule that throws stops the segment where a throwing
 * listener stops it, and the exception reaches the caller.
 *
 * <p>Registered in the module's {@code prepare()}, as every service is, and handed its rules in {@code start()}, which
 * every caller of the service waits for, since each requires the trace analyzer.
 */
@Slf4j
public class SwNativeRuleRunner implements ISegmentParserService {
    private final ModuleManager moduleManager;
    private final TALSourceTypeProvider input;
    private NativeTraceAnalysisConfig config;
    private SearchableTracesTagsWatcher searchableTags;
    /**
     * Written last by {@link #loadRules}, so a thread that reads the rules reads the services set before them.
     */
    private volatile List<TalDSL> rules;

    /**
     * @param moduleManager the manager serving the agent analyzer's settings and the core's searchable tags
     * @param input         the native input, which the rules' functions read the span through
     */
    public SwNativeRuleRunner(final ModuleManager moduleManager, final TALSourceTypeProvider input) {
        this.moduleManager = moduleManager;
        this.input = input;
    }

    /**
     * Read the services the analysis needs and take the rules. Called once, from {@code start()}, where reaching
     * other modules is allowed.
     *
     * @param nativeRules the native rules, in the order they run
     */
    public void loadRules(final List<TalDSL> nativeRules) {
        this.config = moduleManager.find(AnalyzerModule.NAME).provider().getService(NativeTraceAnalysisConfig.class);
        this.searchableTags = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class)
                                           .getSearchableTracesTags();
        this.rules = List.copyOf(nativeRules);
    }

    /**
     * @param segmentObject a segment as the agent sent it
     */
    @Override
    public void send(final SegmentObject segmentObject) {
        final List<TalDSL> loaded = rules;
        if (loaded == null) {
            throw new IllegalStateException("the native TAL rules are not loaded; the agent analyzer is not loaded");
        }
        if (segmentObject.getSpansCount() == 0) {
            return;
        }
        final SwNativeSegmentState segment = new SwNativeSegmentState(segmentObject);
        parseSegment(segment);

        final int ruleCount = loaded.size();
        final Object[][] segmentDefs = new Object[ruleCount][];
        final List<List<QueuedBuild>> builds = new ArrayList<>(ruleCount);
        for (int r = 0; r < ruleCount; r++) {
            segmentDefs[r] = new Object[loaded.get(r).getExpression().segmentDefCount()];
            builds.add(new ArrayList<>());
        }
        final ExecutionContext ctx = new ExecutionContext();
        ctx.setSourceTypeProvider(input);
        // The metadata the engine reads, which no native rule can: the span's start, the default timestamp of a
        // metrics {} block. One per segment run, moved to each span.
        final SpanMetadata meta = SpanMetadata.builder().build();
        for (int s = 0; s < segmentObject.getSpansCount(); s++) {
            final SpanObject span = segmentObject.getSpans(s);
            meta.setStartTime(span.getStartTime());
            if (span.getSpanId() == 0) {
                for (int r = 0; r < ruleCount; r++) {
                    final TalDSL rule = loaded.get(r);
                    if (rule.getExpression().hasFirst()) {
                        ctx.bindNativeRun(meta, span, segment, segmentDefs[r]);
                        rule.getExpression().executeFirst(rule.getFilterSpec(), ctx);
                        keepQueued(ctx, builds.get(r));
                    }
                }
            }
            final SpanType type = span.getSpanType();
            if (type != SpanType.Entry && type != SpanType.Exit && type != SpanType.Local) {
                log.error("span type value was unexpected, span type name: {}", type.name());
            }
            for (int r = 0; r < ruleCount; r++) {
                final TalDSL rule = loaded.get(r);
                ctx.bindNativeRun(meta, span, segment, segmentDefs[r]);
                rule.getExpression().execute(rule.getFilterSpec(), ctx);
                keepQueued(ctx, builds.get(r));
            }
        }
        for (int r = 0; r < ruleCount; r++) {
            final TalDSL rule = loaded.get(r);
            final TalExpression expression = rule.getExpression();
            for (final QueuedBuild build : builds.get(r)) {
                final SpanObject span = (SpanObject) build.getInput();
                meta.setStartTime(span.getStartTime());
                ctx.bindNativeRun(meta, span, segment, segmentDefs[r]);
                expression.executeBuild(build.getIndex(), rule.getFilterSpec(), ctx, build.getCaptured());
            }
        }
    }

    /**
     * {@code SegmentAnalysisListener.parseSegment}: the start and end fold, the error fold under the configured
     * strategy, the searchable tags, the duration capped at {@code Integer.MAX_VALUE}, then the sampling decision.
     */
    private void parseSegment(final SwNativeSegmentState segment) {
        final SegmentObject segmentObject = segment.getSegmentObject();
        long startTimestamp = 0;
        long endTimestamp = 0;
        boolean isError = false;
        final List<Tag> tags = new ArrayList<>();
        for (final SpanObject span : segmentObject.getSpansList()) {
            if (startTimestamp == 0 || startTimestamp > span.getStartTime()) {
                startTimestamp = span.getStartTime();
            }
            if (span.getEndTime() > endTimestamp) {
                endTimestamp = span.getEndTime();
            }
            isError = isError || config.isError(span);
            appendSearchableTags(span, tags);
        }
        final long accurateDuration = endTimestamp - startTimestamp;
        final int duration = accurateDuration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) accurateDuration;
        segment.setStartTime(startTimestamp);
        segment.setEndTime(endTimestamp);
        segment.setLatency(duration);
        segment.setError(isError);
        segment.setTags(tags);
        segment.setSampled(config.shouldSample(segmentObject, duration, isError));
    }

    private void appendSearchableTags(final SpanObject span, final List<Tag> tags) {
        for (final KeyStringValuePair tag : span.getTagsList()) {
            if (!searchableTags.getSearchableTags().contains(tag.getKey())) {
                continue;
            }
            final Tag spanTag = new Tag(tag.getKey(), tag.getValue());
            if (tag.getValue().length() > Tag.TAG_LENGTH || spanTag.toString().length() > Tag.TAG_LENGTH) {
                if (log.isDebugEnabled()) {
                    log.debug("Segment tag : {} length > : {}, dropped", spanTag, Tag.TAG_LENGTH);
                }
                continue;
            }
            if (!tags.contains(spanTag)) {
                tags.add(spanTag);
            }
        }
    }

    private static void keepQueued(final ExecutionContext ctx, final List<QueuedBuild> builds) {
        if (!ctx.isAborted() && !ctx.getQueuedBuilds().isEmpty()) {
            builds.addAll(ctx.getQueuedBuilds());
        }
    }
}
