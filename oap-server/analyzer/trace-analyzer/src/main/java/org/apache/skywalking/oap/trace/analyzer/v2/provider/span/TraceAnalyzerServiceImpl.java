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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalDSL;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;

/**
 * Runs every rule of the span's layer and merges their verdicts.
 *
 * <p>A rule that aborted did not vote. The span is persisted when every rule that voted said keep; tags merge
 * last-writer-wins, which is the rule {@code SpanListenerManager} uses among the listeners.
 */
@Slf4j
public class TraceAnalyzerServiceImpl implements ITraceAnalyzerService {
    private final ModuleManager moduleManager;
    private final SpanFilterListener.Factory factory;
    private final KnownServices knownServices;
    private final SpanPairing spanPairing;
    private final SeenSpans seenSpans;

    /**
     * Resolved on the first record a rule writes rather than at construction: the service is registered in
     * {@code prepare()}, where reaching another module is not yet allowed.
     */
    private volatile SourceReceiver sourceReceiver;

    public TraceAnalyzerServiceImpl(final ModuleManager moduleManager,
                                    final SpanFilterListener.Factory factory,
                                    final KnownServices knownServices,
                                    final SpanPairing spanPairing,
                                    final SeenSpans seenSpans) {
        this.moduleManager = moduleManager;
        this.factory = factory;
        this.knownServices = knownServices;
        this.spanPairing = spanPairing;
        this.seenSpans = seenSpans;
    }

    private SourceReceiver sourceReceiver() {
        if (sourceReceiver == null) {
            sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        }
        return sourceReceiver;
    }

    @Override
    public SpanAnalysisResult doAnalysis(final SpanMetadata meta,
                                         final Object span,
                                         final Map<String, String> resourceAttributes,
                                         final Map<String, String> scopeAttributes) {
        // No rules, which is how an operator turns the analysis off: nothing to run, so no registry, no pairing
        // fact and no dedup entry either, and in a cluster no pairing traffic.
        if (!factory.hasRules()) {
            return SpanAnalysisResult.CONTINUE;
        }
        // A retried span changes nothing: no metric, no pairing, no vote. The receiver keeps its own decision.
        if (!seenSpans.firstSight(meta.getTraceId(), meta.getSpanId())) {
            log.debug("Span {}/{} was analyzed already; skipped", meta.getTraceId(), meta.getSpanId());
            return SpanAnalysisResult.CONTINUE;
        }
        // Recorded before any rule runs, and whether or not one matches: a service that emits spans is a service.
        knownServices.record(meta.getService());
        final Layer layer = resolveLayer(meta);
        if (layer == null && StringUtil.isNotEmpty(meta.getLayer())) {
            // An unknown layer name is reported once per span rather than silently analyzed by the auto rules,
            // which would attribute the span to a layer the operator did not ask for.
            log.warn("Span carries an unknown service.layer '{}'; it is not analyzed", meta.getLayer());
            return SpanAnalysisResult.CONTINUE;
        }
        // Before the rules, and whether or not one matches: the callee side of the pairing is a fact of the span,
        // and its service id carries the flag the resource's layer gives it, which is what its metrics carry too.
        // SERVER only: a CONSUMER span's parent is the PRODUCER span, and pairing those two would register the
        // broker's address as an alias of the consumer, pulling every producer's edge off the broker.
        if (SpanMetadata.KIND_SERVER.equals(meta.getKind()) && StringUtil.isNotEmpty(meta.getParentSpanId())) {
            spanPairing.serverSeen(meta, MeterEntity.isNormal(layer));
        }

        final List<SpanFilterListener.RuleBinding> rules = factory.rulesFor(layer, span);
        if (rules.isEmpty()) {
            return SpanAnalysisResult.CONTINUE;
        }

        boolean persist = true;
        boolean enforced = false;
        final Map<String, String> tags = new LinkedHashMap<>();

        // The span's attributes, indexed by the first rule that reads them and handed to the next, so a span costs
        // one pass over its attribute list however many rules run; every rule of a layer reads the same input
        // object, which is why one index serves them all.
        Map<String, String> attributeIndex = null;
        for (final SpanFilterListener.RuleBinding binding : rules) {
            // Each rule gets its own metadata copy, so a rule that overwrites meta.service does not disturb the
            // next rule's view of the same span.
            final SpanMetadata ruleMeta = copyOf(meta);
            final ExecutionContext ctx = new ExecutionContext();
            ctx.setSourceTypeProvider(binding.provider);
            ctx.init(ruleMeta, span, resourceAttributes, scopeAttributes);
            ctx.reuseAttributeIndex(attributeIndex);
            try {
                binding.dsl.evaluate(ctx);
            } catch (final RuntimeException e) {
                reportFailure(binding.dsl, e);
                continue;
            } finally {
                if (attributeIndex == null) {
                    attributeIndex = ctx.builtAttributeIndex();
                }
            }
            if (ctx.isAborted()) {
                continue;
            }
            writeRecord(ctx, ruleMeta, span);
            tags.putAll(ctx.getAdditionalTags());
            if (ctx.isPersistVoted()) {
                persist = persist && ctx.isPersist();
                enforced = enforced || ctx.isEnforced();
            }
        }

        return SpanAnalysisResult.builder()
                                .persist(persist)
                                .enforced(persist && enforced)
                                .additionalTags(tags)
                                .build();
    }

    /**
     * A rule that throws is a defect in the rule, and it repeats on every span the rule sees, so the stack trace is
     * logged once per rule and the repeats at debug, which keeps the receiver thread off the log.
     */
    private static void reportFailure(final TalDSL dsl, final RuntimeException e) {
        if (dsl.firstFailure()) {
            log.warn("TAL rule {} failed on a span and is skipped for it; its later failures are logged at debug",
                     dsl.getRuleName(), e);
        } else if (log.isDebugEnabled()) {
            log.debug("TAL rule {} failed on a span", dsl.getRuleName(), e);
        }
    }

    /**
     * Dispatch the record a rule with an {@code outputType} built, unless its sink dropped it.
     */
    private void writeRecord(final ExecutionContext ctx, final SpanMetadata meta, final Object span) {
        final TALOutputBuilder builder = ctx.outputAsBuilder();
        if (builder == null || !ctx.isSinkKept()) {
            return;
        }
        try {
            builder.init(meta, span, moduleManager);
            builder.complete(sourceReceiver());
        } catch (final RuntimeException e) {
            log.warn("A TAL rule's output record could not be written", e);
        }
    }

    /**
     * @param meta the span's layer name as the receiver read it
     * @return the layer; null when the span carries none, which the auto rules serve, and when the name is not
     * registered, which {@link #doAnalysis} reports. {@code Layer.nameOf} is not used here because it answers
     * {@code UNDEFINED} for an unknown name, which no rule serves either, but silently.
     */
    private static Layer resolveLayer(final SpanMetadata meta) {
        if (StringUtil.isEmpty(meta.getLayer())) {
            return null;
        }
        try {
            return Layer.valueOf(meta.getLayer());
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private static SpanMetadata copyOf(final SpanMetadata meta) {
        return SpanMetadata.builder()
                           .service(meta.getService())
                           .serviceInstance(meta.getServiceInstance())
                           .endpoint(meta.getEndpoint())
                           .layer(meta.getLayer())
                           .kind(meta.getKind())
                           .statusCode(meta.getStatusCode())
                           .statusMessage(meta.getStatusMessage())
                           .startTime(meta.getStartTime())
                           .timestamp(meta.getTimestamp())
                           .latency(meta.getLatency())
                           .traceId(meta.getTraceId())
                           .spanId(meta.getSpanId())
                           .parentSpanId(meta.getParentSpanId())
                           .root(meta.isRoot())
                           .scopeName(meta.getScopeName())
                           .scopeVersion(meta.getScopeVersion())
                           .build();
    }
}
