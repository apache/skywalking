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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * One rule's state for one span: what it reads, what it decided and what it built.
 *
 * <p>A fresh context is created per rule per span, so a rule that overwrites {@code meta.service} does not
 * disturb the next rule's view of the same span.
 */
public class ExecutionContext {
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
     * The instrumentation scope's attributes, indexed on first use like {@link #attributeIndex}.
     */
    private Map<String, String> scopeAttributeIndex;

    /**
     * Set by {@code abort {}}: the rule stops and writes nothing, and its votes do not count.
     */
    @Getter
    private boolean aborted;

    /**
     * The rule's vote on the span. A rule without a {@code persist {}} block abstains and leaves this true.
     */
    @Getter
    private boolean persist = true;

    /**
     * Whether the rule voted at all, which is what separates "keep" from "abstain" when the votes are merged.
     */
    @Getter
    private boolean persistVoted;

    /**
     * Set by an {@code enforcer {}} in a {@code persist {}} block: keep the span past this rule's samplers and
     * past the receiver's trace-id head sampling.
     */
    @Getter
    private boolean enforced;

    /**
     * Whether the record the rule built should be written; only meaningful for a rule with an output type.
     */
    @Getter
    private boolean sinkKept = true;

    /**
     * Set by an {@code enforcer {}} in a {@code sink {}} block: keep the record past this rule's samplers.
     */
    @Getter
    private boolean recordEnforced;

    /**
     * The record under construction, an instance of the rule's {@code outputType}.
     */
    @Getter
    @Setter
    private Object output;

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
     * Start a new execution against one span.
     *
     * @param meta               the span's metadata view; the rule may overwrite its identity fields
     * @param input              the wire object
     * @param resourceAttributes the resource attributes as strings
     * @param scopeAttributes    the instrumentation scope's attributes, or null to read them from the provider
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
        this.scopeAttributeIndex = scopeAttributes;
        this.aborted = false;
        this.persist = true;
        this.persistVoted = false;
        this.enforced = false;
        this.sinkKept = true;
        this.recordEnforced = false;
        this.output = null;
        this.additionalTags.clear();
        this.traffickedEntities.clear();
        return this;
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
     * @return the span's attributes, built on first use from the input's provider
     */
    public Map<String, String> spanAttributes() {
        if (attributeIndex == null) {
            attributeIndex = sourceTypeProvider == null || input == null
                ? Collections.emptyMap() : sourceTypeProvider.spanAttributes(input);
        }
        return attributeIndex;
    }

    /**
     * @return the instrumentation scope's attributes, built on first use
     */
    public Map<String, String> scopeAttributes() {
        if (scopeAttributeIndex == null) {
            scopeAttributeIndex = sourceTypeProvider == null || input == null
                ? Collections.emptyMap() : sourceTypeProvider.scopeAttributes(input);
        }
        return scopeAttributeIndex;
    }

    public void abort() {
        this.aborted = true;
    }

    /**
     * Record a vote on the span's persistence.
     *
     * @param keep whether this statement votes to keep the span
     */
    public void votePersist(final boolean keep) {
        this.persistVoted = true;
        this.persist = this.persist && keep;
    }

    public void enforce() {
        this.persistVoted = true;
        this.enforced = true;
        this.persist = true;
    }

    public void dropRecord() {
        this.sinkKept = false;
    }

    public void enforceRecord() {
        this.recordEnforced = true;
        this.sinkKept = true;
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

    /**
     * @return the record under construction, typed as a builder
     */
    public TALOutputBuilder outputAsBuilder() {
        return output instanceof TALOutputBuilder ? (TALOutputBuilder) output : null;
    }
}
