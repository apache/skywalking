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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * One rule's state for one span: what it reads, what it decided and what it built.
 *
 * <p>A fresh context is created per rule per span, so a rule that overwrites {@code meta.service} does not
 * disturb the next rule's view of the same span.
 */
public class ExecutionContext {
    private static final Object[] NO_SEGMENT_DEFS = new Object[0];

    @Getter
    private SpanMetadata meta;

    /**
     * The wire object, an instance of the rule's declared input type.
     */
    @Getter
    private Object input;

    /**
     * The resource attributes as strings, what {@code resource()} reads. Empty for an input with no resource.
     */
    @Getter
    private Map<String, String> resourceAttributes = Collections.emptyMap();

    @Getter
    @Setter
    private TALSourceTypeProvider sourceTypeProvider;

    @Getter
    @Setter
    private GateHolder debugHolder;

    /**
     * The span's own attributes, indexed on the first {@code attr()} of this execution and reused after, so a
     * rule with many lookups costs one pass over the attribute list.
     */
    private Map<String, String> attributeIndex;

    /**
     * The instrumentation scope's attributes the receiver passed to {@link #init}.
     */
    private Map<String, String> scopeAttributeIndex = Collections.emptyMap();

    /**
     * Set by {@code abort {}}: the rule stops and writes nothing, and its votes do not count.
     */
    @Getter
    private boolean aborted;

    /**
     * Set by {@code buildNetworkAlias}: the span is the callee half of a call whose alias this rule composes.
     */
    @Getter
    private boolean networkAliasRequested;

    /**
     * Set by {@code prepareNetworkAlias}: the peer this span dialed, the caller half of a call, which pairs with the
     * SERVER span whose parent is this span. Sent once the rules have run, by the first rule that was not aborted, as
     * the callee half is.
     */
    @Getter
    private String networkAliasPeer;

    /**
     * Whether the rule voted on the span at all, which is what separates "keep" from "abstain" when the votes are
     * merged; a rule without a {@code persist {}} block that ran abstains.
     */
    @Getter
    private boolean persistVoted;

    /**
     * Set by {@code dropper {}} in a {@code persist {}} block. It wins over everything else in the rule.
     */
    private boolean spanDropped;

    /**
     * Set by a {@code rateLimit} in a {@code persist {}} block that ran out of budget.
     */
    private boolean spanSampledOut;

    /**
     * Set by an {@code enforcer {}} in a {@code persist {}} block: keep the span past this rule's samplers, before
     * or after it, and past the receiver's trace-id head sampling. A {@code dropper {}} still drops it.
     */
    @Getter
    private boolean enforced;

    /**
     * Tags the rule appended to the span, in the order they were written.
     */
    @Getter
    private final Map<String, String> additionalTags = new LinkedHashMap<>();

    /**
     * Entities this execution has already generated traffic for, so several {@code metrics {}} blocks sharing a
     * scope register their entity once. Keyed by scope as well as id: an instance and an endpoint of one service
     * with the same name have the same id.
     */
    private final Set<String> traffickedEntities = new HashSet<>();

    /**
     * The rule's {@code segmentDef} values for the segment this run belongs to, one array that every run of the rule
     * on that segment reads and writes; see {@link #bindNativeRun}.
     */
    @Getter
    private Object[] segmentDefs = NO_SEGMENT_DEFS;
    /**
     * The native input's segment run: the segment the agent sent and what the runner derived from it; null for
     * another input. Set by the native runner beside the span, the input, for every run of the segment.
     */
    @Getter
    private SwNativeSegmentState nativeSegment;

    /**
     * The {@code build {}} blocks this run queued, in the order it reached them. The runner keeps them for the
     * segment's build phase only when the run was not aborted.
     */
    @Getter
    private final List<QueuedBuild> queuedBuilds = new ArrayList<>();

    /**
     * Start a new execution against one span.
     *
     * @param meta               the span's metadata view; the rule may overwrite its identity fields
     * @param input              the wire object
     * @param resourceAttributes the resource attributes as strings
     * @param scopeAttributes    the instrumentation scope's attributes, or null when the input has none
     * @return this context, ready to run
     */
    public ExecutionContext init(final SpanMetadata meta,
                                 final Object input,
                                 final Map<String, String> resourceAttributes,
                                 final Map<String, String> scopeAttributes) {
        this.meta = meta;
        this.input = input;
        this.resourceAttributes = resourceAttributes == null ? Collections.emptyMap() : resourceAttributes;
        this.attributeIndex = null;
        this.scopeAttributeIndex = scopeAttributes == null ? Collections.emptyMap() : scopeAttributes;
        this.aborted = false;
        this.networkAliasRequested = false;
        this.networkAliasPeer = null;
        this.persistVoted = false;
        this.spanDropped = false;
        this.spanSampledOut = false;
        this.enforced = false;
        this.additionalTags.clear();
        this.traffickedEntities.clear();
        return this;
    }

    /**
     * Start one native rule's run on a span, in place of {@link #init}, on a context only native runs use. A native
     * rule has the language's statements and the native input's ({@code TALConstruct.NATIVE_SEGMENT}) only, which
     * change no more than whether the run aborted, the builds it queued and the entities its {@code metrics {}}
     * marked; so only those are reset, and the state the other inputs' statements set stays as a new context has it.
     * A segment runs every rule on every span, most runs abort at their first line, and this costs a few stores.
     *
     * @param meta        the span's metadata, which the engine reads and a native rule cannot write
     * @param span        the span
     * @param segment     the segment run
     * @param segmentDefs the array of {@code TalExpression.segmentDefCount()} values the rule's runs on the segment
     *                    share
     */
    public void bindNativeRun(final SpanMetadata meta, final Object span, final SwNativeSegmentState segment,
                              final Object[] segmentDefs) {
        this.meta = meta;
        this.input = span;
        this.nativeSegment = segment;
        this.segmentDefs = segmentDefs;
        this.aborted = false;
        if (!queuedBuilds.isEmpty()) {
            queuedBuilds.clear();
        }
        if (!traffickedEntities.isEmpty()) {
            traffickedEntities.clear();
        }
    }

    /**
     * {@code build {}} — queue the block, with the values of the variables the run has here and the run's input.
     *
     * @param index    the block, numbered in the rule
     * @param captured the values of the {@code def} variables in scope, in the order the block's method reads them
     */
    public void queueBuild(final int index, final Object[] captured) {
        queuedBuilds.add(new QueuedBuild(index, captured, input));
    }

    /**
     * Hand this execution the attribute index another rule built for the same input, so it is not built twice
     * for one span. Null leaves this execution to build its own on first use.
     *
     * @param index the index a previous execution over the same input built, or null
     */
    public void reuseAttributeIndex(final Map<String, String> index) {
        this.attributeIndex = index;
    }

    /**
     * @return the attribute index this execution built or was handed, or null when no statement read the attributes
     */
    public Map<String, String> builtAttributeIndex() {
        return attributeIndex;
    }

    /**
     * @return the OTLP span's attributes, built on first use from the OTLP provider; empty for another input
     */
    public Map<String, String> spanAttributes() {
        if (attributeIndex == null) {
            attributeIndex = otlpProvider() == null || input == null
                ? Collections.emptyMap() : otlpProvider().spanAttributes(input);
        }
        return attributeIndex;
    }

    /**
     * @return the instrumentation scope's attributes the receiver passed, which the enclosing {@code ScopeSpans}
     * carries rather than the span; empty when it passed none
     */
    public Map<String, String> scopeAttributes() {
        return scopeAttributeIndex;
    }

    /**
     * @return the input's provider when the input is an OTLP span, which reads the span's attributes; null otherwise
     */
    private OtlpTALSourceTypeProvider otlpProvider() {
        return sourceTypeProvider instanceof OtlpTALSourceTypeProvider
            ? (OtlpTALSourceTypeProvider) sourceTypeProvider : null;
    }

    public void abort() {
        this.aborted = true;
    }

    /**
     * {@code buildNetworkAlias <address>} — mark this SERVER span as the callee half of a call. The address
     * expression is not evaluated here: it reads the matched caller half, {@code pairing.*}, and runs on the
     * trace's owner after the match, see {@code TalDSL#networkAliasAddress}.
     */
    public void requestNetworkAlias() {
        this.networkAliasRequested = true;
    }

    /**
     * {@code prepareNetworkAlias <peer>} — mark this span as the caller half of a call to the peer: a CLIENT span, or
     * a SERVER span that passed its own context to the peer. The first peer a rule names is kept; an empty one names
     * none.
     *
     * @param peer the peer as the rule named it
     */
    public void prepareNetworkAlias(final String peer) {
        if (networkAliasPeer == null && peer != null && !peer.isEmpty()) {
            this.networkAliasPeer = peer;
        }
    }

    /**
     * Record a vote on the span's persistence: {@code persist {}} running votes keep, {@code dropper {}} votes drop.
     *
     * @param keep whether this statement votes to keep the span
     */
    public void votePersist(final boolean keep) {
        this.persistVoted = true;
        if (!keep) {
            this.spanDropped = true;
        }
    }

    /**
     * A {@code rateLimit} in {@code persist {}} ran out of budget; an {@code enforcer {}} of the rule overrides it.
     */
    public void sampleOutSpan() {
        this.persistVoted = true;
        this.spanSampledOut = true;
    }

    public void enforce() {
        this.persistVoted = true;
        this.enforced = true;
    }

    /**
     * @return the rule's vote on the span: kept unless a {@code dropper {}} dropped it, or a sampler did and no
     * {@code enforcer {}} kept it
     */
    public boolean isPersist() {
        return !spanDropped && (enforced || !spanSampledOut);
    }

    public void addTag(final String key, final String value) {
        if (key != null && value != null) {
            additionalTags.put(key, value);
        }
    }

    /**
     * @param entity the entity a metric was just accepted for
     * @return true the first time this execution sees the entity, so its traffic is generated once
     */
    public boolean markTrafficked(final MeterEntity entity) {
        return traffickedEntities.add(entity.getScopeType() + "/" + entity.id());
    }
}
