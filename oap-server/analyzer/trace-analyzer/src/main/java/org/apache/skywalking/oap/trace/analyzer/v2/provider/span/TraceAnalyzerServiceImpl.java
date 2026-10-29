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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    /**
     * Unknown {@code service.layer} names already warned about. Bounded by the names senders spell; the cap
     * only guards against a sender that varies the name per span.
     */
    private static final Set<String> REPORTED_UNKNOWN_LAYERS = ConcurrentHashMap.newKeySet();

    private static final int REPORTED_UNKNOWN_LAYERS_CAP = 1000;

    /**
     * Services already reported for sending spans without a {@code service.layer}, which no rule sees. Bounded the
     * same way.
     */
    private static final Set<String> REPORTED_UNLAYERED_SERVICES = ConcurrentHashMap.newKeySet();

    private final ModuleManager moduleManager;
    private final SpanFilterListener.Factory factory;
    private final SpanPairing spanPairing;
    private final SeenSpans seenSpans;

    /**
     * Resolved on the first record a rule writes rather than at construction: the service is registered in
     * {@code prepare()}, where reaching another module is not yet allowed.
     */
    private volatile SourceReceiver sourceReceiver;

    public TraceAnalyzerServiceImpl(final ModuleManager moduleManager,
                                    final SpanFilterListener.Factory factory,
                                    final SpanPairing spanPairing,
                                    final SeenSpans seenSpans) {
        this.moduleManager = moduleManager;
        this.factory = factory;
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
        // A span is analyzed by the rules of the layer its resource names, and by nothing else: one without a
        // registered layer is stored, not analyzed. Each is reported once, per name or per service, not per span.
        final Layer layer = resolveLayer(meta);
        if (layer == null) {
            if (StringUtil.isNotEmpty(meta.getLayer())) {
                if (REPORTED_UNKNOWN_LAYERS.size() < REPORTED_UNKNOWN_LAYERS_CAP
                    && REPORTED_UNKNOWN_LAYERS.add(meta.getLayer())) {
                    log.warn("Spans carry an unknown service.layer '{}'; they are not analyzed", meta.getLayer());
                }
            } else if (meta.getService() != null
                // Looked up before the bounded add, since every span of such a service passes here.
                && !REPORTED_UNLAYERED_SERVICES.contains(meta.getService())
                && REPORTED_UNLAYERED_SERVICES.size() < REPORTED_UNKNOWN_LAYERS_CAP
                && REPORTED_UNLAYERED_SERVICES.add(meta.getService())) {
                // Silent otherwise: an application that never set the layer shows up as empty dashboards only.
                log.info("Spans of service '{}' carry no service.layer, so they are stored but not analyzed. Set "
                    + "service.layer on its resource to a layer the TAL rules are declared for, OTLP_GENERAL with "
                    + "the shipped rules, e.g. OTEL_RESOURCE_ATTRIBUTES=service.layer=OTLP_GENERAL, to analyze "
                    + "them.", meta.getService());
            }
            return SpanAnalysisResult.CONTINUE;
        }
        final List<SpanFilterListener.RuleBinding> rules = factory.rulesFor(layer, span);
        if (rules.isEmpty()) {
            // A layer with no rule, as OTLP_MESH, is a deliberate opt-out.
            return SpanAnalysisResult.CONTINUE;
        }
        // A retried span is not counted again: no metric, no pairing. It gets the verdict its first delivery got,
        // so a span a rule dropped stays dropped and an enforced one stays enforced, while its rules stay installed.
        // Only a span some rule runs on takes a slot: the others, unlayered or on a layer with no rule, are most of
        // the traffic and would push the analyzed spans out of the window.
        if (!seenSpans.firstSight(meta.getTraceId(), meta.getSpanId())) {
            log.debug("Span {}/{} was analyzed already; skipped", meta.getTraceId(), meta.getSpanId());
            return seenSpans.verdictOf(meta.getTraceId(), meta.getSpanId());
        }
        // The first rule that ran to the end, without `abort {}`, and asked for a network alias with
        // buildNetworkAlias; the callee half of the pairing carries its key, so the trace's owner composes the
        // alias address with that rule's expression. The half names the service as that rule left it, and the layer
        // its extractor named gives the alias the service id the rule's own metrics use; the span's own layer is the
        // fallback.
        String aliasRule = null;
        SpanMetadata aliasMeta = null;
        // The first rule that ran to the end and named the peer with prepareNetworkAlias sends the caller half, once
        // per span, as the callee half is chosen; an aborted rule sends neither.
        String clientPeer = null;
        SpanMetadata clientMeta = null;

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
            if (aliasRule == null && ctx.isNetworkAliasRequested()) {
                aliasRule = binding.dsl.getRuleName();
                aliasMeta = ruleMeta;
            }
            if (clientPeer == null && ctx.getNetworkAliasPeer() != null) {
                clientPeer = ctx.getNetworkAliasPeer();
                clientMeta = ruleMeta;
            }
            writeRecord(ctx, ruleMeta, span);
            tags.putAll(ctx.getAdditionalTags());
            if (ctx.isPersistVoted()) {
                persist = persist && ctx.isPersist();
                enforced = enforced || ctx.isEnforced();
            }
        }

        if (clientPeer != null) {
            spanPairing.clientSeen(clientMeta, clientPeer);
        }
        if (aliasRule != null) {
            final Layer aliasLayer = resolveLayer(aliasMeta);
            spanPairing.serverSeen(aliasMeta, MeterEntity.isNormal(aliasLayer != null ? aliasLayer : layer),
                layer.name(), aliasRule);
        }

        // The common outcome carries nothing span-specific, so the dedup window holds the shared instance.
        final SpanAnalysisResult result = persist && !enforced && tags.isEmpty()
            ? SpanAnalysisResult.CONTINUE
            : SpanAnalysisResult.builder()
                                .persist(persist)
                                .enforced(persist && enforced)
                                .additionalTags(tags)
                                .build();
        seenSpans.remember(meta.getTraceId(), meta.getSpanId(), result);
        return result;
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
     * Dispatch the record a rule with an {@code outputType} built, when a {@code sink {}} of the rule ran and its
     * votes kept the record.
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
     * @return the layer; null when the span carries none or the name is not registered, neither of which a rule
     * serves, as {@link #doAnalysis} reports. {@code Layer.nameOf} is not used here because it answers
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
                           .parentLocation(meta.getParentLocation())
                           .scopeName(meta.getScopeName())
                           .scopeVersion(meta.getScopeVersion())
                           .build();
    }
}
