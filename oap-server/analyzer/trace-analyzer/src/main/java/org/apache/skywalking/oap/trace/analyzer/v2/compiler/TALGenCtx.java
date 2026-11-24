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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;

/**
 * The state one rule's code generation carries: the resolved types, the local variables, where in the rule the
 * generator is, and the channel {@link TALRootResolver#generateInputRootAccess} uses to report the static type and
 * null checks of the chain it just emitted.
 */
public class TALGenCtx {
    public final TALRootResolver resolver;
    public final Class<?> inputType;

    /**
     * Rule's name, emitted as the first argument of every probe call site.
     */
    public String ruleName = "";

    /**
     * Set by the input-root codegen so a caller can specialize on a primitive leaf; cleared at the start of every
     * value access.
     */
    public Class<?> lastResolvedType;
    /**
     * The element type of the value just emitted when it is a generic {@code List}, so {@code foreach} can type its
     * loop variable; null otherwise.
     */
    public Class<?> lastResolvedElementType;
    /**
     * Whether the value just emitted is a primitive read behind a {@code ?.} guard, boxed to carry its null, which no
     * raw form reads.
     */
    public boolean lastNullChecks;
    /**
     * The unboxed form of the primitive just emitted, for the callers that box or widen it; null when there is none.
     */
    public String lastRawChain;

    /**
     * Whether the method being generated reads the rule's input, which it then casts once at its top.
     */
    public boolean usesInput;

    /**
     * Local variables from {@code def} statements, keyed by the name the rule chose. The declaration is hoisted to
     * the top of the generated method, so a second {@code def} of one name is an assignment, not a new variable.
     */
    public final Map<String, TALLocalVar> localVars = new HashMap<>();
    public final StringBuilder localVarDecls = new StringBuilder();
    public final List<String[]> localVarLvtVars = new ArrayList<>();
    /**
     * The def names declared inside each enclosing {@code foreach} body, innermost last. A name declared in a body
     * belongs to one iteration: it is reset when an iteration starts and is not visible after the body.
     */
    public final Deque<List<String>> blockScopes = new ArrayDeque<>();
    /**
     * Every Java local a def declared in this method, so a name declared again after its scope ended gets a fresh
     * local rather than a second declaration of the same one.
     */
    public final Set<String> declaredJavaNames = new HashSet<>();
    /**
     * The defs that can hold, on some path to the statement being generated, a map or list the rule did not make with
     * {@code emptyMap()} or {@code emptyList()}, which {@code put} and {@code add} do not change. A def outside it
     * holds one the rule made, or null, on every path: a def is null on the paths that did not assign it.
     */
    public final Set<String> foreignCollections = new HashSet<>();
    /**
     * The defs that can hold, on some path to the statement being generated, a number other than a quotient; one
     * outside it holds a quotient, or null, which narrows into a def of a whole or floating number as the division
     * would.
     */
    public final Set<String> otherNumbers = new HashSet<>();
    /**
     * What this generation assumes some assignment of the rule gives each {@code segmentDef}, by its local
     * ({@code _sd_<name>}): a collection the rule did not make, or a number other than a quotient; every generated
     * method starts with these facts.
     */
    public Facts assumedSegmentDefFacts = Facts.none();
    /**
     * What the assignments of this generation give each {@code segmentDef}; {@code TALClassGenerator} generates the
     * rule again until the assumed facts cover these.
     */
    public final Facts learnedSegmentDefFacts = Facts.none();
    /**
     * What each {@code foreach} body (by its block) may start an iteration with, as the generation before learned it;
     * the body's code is generated for that, so it holds on every iteration.
     */
    public Map<Object, Facts> loopEntryFacts = Collections.emptyMap();
    /**
     * What each {@code foreach} body of this generation can end an iteration with; {@code TALClassGenerator}
     * generates the rule again until each body starts from what it can end with.
     */
    public final Map<Object, Facts> learnedLoopFacts = new IdentityHashMap<>();
    /**
     * Whether a {@code foreach} body of this generation can end an iteration with what it did not start with.
     */
    public boolean loopsUnsettled;

    /**
     * The local each held {@code (value)?.chain} expression is held in, by its code.
     */
    public final Map<String, String> heldLocals = new HashMap<>();

    /**
     * The overload each contributed call resolved to, keyed by the call itself, so the code a call emits and the
     * type it is read as come from the same method however often the call is generated or probed.
     */
    public final Map<TALValueAccess, TALFunctionRegistry.Resolved> resolvedCalls = new IdentityHashMap<>();

    /**
     * The fields of the generated class, which every method of the class shares; the network-alias method's
     * context is handed the rule's.
     */
    public ClassMembers classMembers = new ClassMembers();

    /**
     * The prefix every metric of this rule's file carries, from the file's {@code metricPrefix}.
     */
    public String metricPrefix;

    /**
     * Every metric the rule declares, including those inside {@code if} branches, collected so
     * {@code MeterSystem.create} runs for each before the class is loaded.
     */
    public final List<TALMetricDeclaration> metricDeclarations = new ArrayList<>();

    /**
     * {@code def} variables of the method being generated that hold an entity, mapped to the {@code ScopeType} their
     * constructor implies, so a {@code scope} naming one registers its metric under the right scope.
     */
    public final Map<String, String> entityScopeTypes = new HashMap<>();

    /**
     * How deep the generator is inside an {@code extractor} and inside a {@code persist} block,
     * so a block that belongs at the filter level is refused anywhere else.
     */
    public int extractorDepth;
    public int voteDepth;

    /**
     * The rule's {@code buildNetworkAlias}, whose address expression becomes the generated
     * {@code networkAliasAddress} method; null when the rule builds no alias.
     */
    public TALScriptModel.NetworkAliasStatement networkAliasBuild;
    /**
     * The alias of the rule's input, which the constructs a rule uses are checked against; see {@link TALInputs}.
     */
    public String inputAlias;

    /**
     * The rule's {@code segmentDef} variables, each to its slot in the segment's array, in the order the rule's text
     * first declares them.
     */
    public Map<String, Integer> segmentDefSlots = Collections.emptyMap();
    /**
     * What each {@code segmentDef} holds in this generation of the rule; a name not settled yet holds an
     * {@code Object}. {@code TALClassGenerator.compile} generates the rule until these match
     * {@link #learnedSegmentDefTypes}, since a variable can be read before the statement that types it.
     */
    public Map<String, SegmentDefType> segmentDefTypes = Collections.emptyMap();
    /**
     * What the first generated {@code segmentDef} statement of each name gave its value in this generation.
     */
    public final Map<String, SegmentDefType> learnedSegmentDefTypes = new HashMap<>();
    /**
     * Set while a generation only learns the {@code segmentDef} types: a statement that fails is skipped rather than
     * failing the rule, so the statements after it are still generated and a declaration after a read is reached.
     * The generation that follows, with the types settled, reports every error.
     */
    public boolean discoveringTypes;

    /**
     * How deep the generator is inside a {@code build {}} block, where a native rule writes its outputs.
     */
    public int buildDepth;

    /**
     * Whether the condition being generated is an {@code if}'s, whose operands a debug capture records.
     */
    public boolean captureOperands;

    /**
     * Whether the method being generated is the rule's {@code first {}} run, whose steps a debug capture shows as
     * the {@code first} stage.
     */
    public boolean firstMethod;
    /**
     * Every {@code build {}} block the rule's runs queue, by its index; each becomes a method of its own, generated
     * after the method that queues it.
     */
    public final List<BuildSite> builds = new ArrayList<>();

    private int localNameCounter;

    public TALGenCtx(final TALRootResolver resolver, final Class<?> inputType) {
        this.resolver = resolver;
        this.inputType = inputType;
    }

    /**
     * @param prefix a short prefix naming what the variable holds
     * @return a variable name unique within the generated class
     */
    public String nextLocalName(final String prefix) {
        return prefix + localNameCounter++;
    }

    public void clearExtraLogResult() {
        lastResolvedType = null;
        lastResolvedElementType = null;
        lastNullChecks = false;
        lastRawChain = null;
    }

    /**
     * Start a generated method: its own locals and chain caches, at the filter level, with every {@code segmentDef}
     * read from the segment's array into a local of its type, holding what every assignment of the rule gives it.
     */
    public void beginMethod() {
        usesInput = false;
        localVars.clear();
        localVarDecls.setLength(0);
        localVarLvtVars.clear();
        blockScopes.clear();
        declaredJavaNames.clear();
        heldLocals.clear();
        entityScopeTypes.clear();
        extractorDepth = 0;
        voteDepth = 0;
        buildDepth = 0;
        firstMethod = false;
        for (final Map.Entry<String, Integer> slot : segmentDefSlots.entrySet()) {
            final SegmentDefType type = segmentDefTypes.get(slot.getKey());
            final Class<?> resolved = type == null ? Object.class : type.type;
            final String javaVar = "_sd_" + slot.getKey();
            final String typeName = TALCodegenHelper.javaTypeName(resolved);
            declaredJavaNames.add(javaVar);
            localVars.put(slot.getKey(),
                new TALLocalVar(javaVar, resolved, type == null ? null : type.elementType, slot.getValue()));
            if (type != null && type.entityScope != null) {
                entityScopeTypes.put(slot.getKey(), type.entityScope);
            }
            localVarDecls.append("  ").append(typeName).append(" ").append(javaVar).append(" = (").append(typeName)
                         .append(") ctx.getSegmentDefs()[").append(slot.getValue()).append("];\n");
            localVarLvtVars.add(new String[] {javaVar, TALCodegenHelper.descriptor(resolved)});
        }
        restore(assumedSegmentDefFacts);
    }

    /**
     * @param javaVar a def's local, or the code of a value no def holds
     * @return whether a def holds a map or list the rule made, or null; a value no def holds is none
     */
    public boolean ownsCollection(final String javaVar) {
        return holdsFact(javaVar, foreignCollections);
    }

    /**
     * @return whether a def holds a quotient, or null
     */
    public boolean holdsQuotient(final String javaVar) {
        return holdsFact(javaVar, otherNumbers);
    }

    private boolean holdsFact(final String javaVar, final Set<String> without) {
        return !without.contains(javaVar) && localVars.values().stream().anyMatch(v -> v.javaVarName.equals(javaVar));
    }

    /**
     * Records what a {@code foreach} body, generated for the given start, can end an iteration with.
     */
    public void learnLoop(final Object block, final Facts start, final Facts exit) {
        if (!start.covers(exit)) {
            loopsUnsettled = true;
        }
        learnedLoopFacts.merge(block, exit, Facts::join);
    }

    /**
     * Records what a def holds after an assignment, and what the assignments give a {@code segmentDef}.
     */
    public void assigned(final TALLocalVar var, final boolean ownCollection, final boolean quotient) {
        assumeUnassigned(var.javaVarName, ownCollection, quotient);
        if (var.segmentSlot >= 0 && !ownCollection) {
            learnedSegmentDefFacts.foreignCollections.add(var.javaVarName);
        }
        if (var.segmentSlot >= 0 && !quotient) {
            learnedSegmentDefFacts.otherNumbers.add(var.javaVarName);
        }
    }

    /**
     * What a local no assignment of the rule types holds, such as a loop variable: a map or list the rule made, or
     * not, and a quotient, or not.
     */
    public void assumeUnassigned(final String javaVar, final boolean ownCollection, final boolean quotient) {
        mark(foreignCollections, javaVar, !ownCollection);
        mark(otherNumbers, javaVar, !quotient);
    }

    private static void mark(final Set<String> set, final String javaVar, final boolean add) {
        if (add) {
            set.add(javaVar);
        } else {
            set.remove(javaVar);
        }
    }

    /**
     * @return what {@link #foreignCollections} and {@link #otherNumbers} hold now
     */
    public Facts facts() {
        return new Facts(foreignCollections, otherNumbers);
    }

    /**
     * Adds what the facts say a def can hold.
     */
    public void include(final Facts facts) {
        foreignCollections.addAll(facts.foreignCollections);
        otherNumbers.addAll(facts.otherNumbers);
    }

    /**
     * @param facts what holds where the generator goes on, null where no path reaches
     */
    public void restore(final Facts facts) {
        if (facts == null) {
            return;
        }
        foreignCollections.clear();
        foreignCollections.addAll(facts.foreignCollections);
        otherNumbers.clear();
        otherNumbers.addAll(facts.otherNumbers);
    }

    /**
     * @param key       a bound function object's registry key
     * @param className its class
     * @return the field of the generated class holding the object, which the generator sets once, when it makes the
     * rule, from the registry the rule compiled against
     */
    public String functionObjectVar(final String key, final String className) {
        return classMembers.functionFields.computeIfAbsent(key, k -> new String[] {
            "_fn" + classMembers.functionFields.size(), className
        })[0];
    }

    /**
     * The fields a generated class holds besides its logger: the bound function objects its methods call.
     */
    public static final class ClassMembers {
        /**
         * Registry key to its field's name and the object's class name.
         */
        public final Map<String, String[]> functionFields = new LinkedHashMap<>();
    }

    /**
     * What the defs hold at a point of a rule: {@link #foreignCollections} and {@link #otherNumbers}.
     */
    public static final class Facts {
        final Set<String> foreignCollections;
        final Set<String> otherNumbers;

        Facts(final Set<String> foreignCollections, final Set<String> otherNumbers) {
            this.foreignCollections = new HashSet<>(foreignCollections);
            this.otherNumbers = new HashSet<>(otherNumbers);
        }

        /**
         * @return facts that every def holds a map or list the rule made and a quotient
         */
        public static Facts none() {
            return new Facts(Collections.emptySet(), Collections.emptySet());
        }

        /**
         * @return what holds where two paths join, on both of them; either is null where no path reaches
         */
        public static Facts join(final Facts a, final Facts b) {
            if (a == null || b == null) {
                return a == null ? b : a;
            }
            final Facts both = new Facts(a.foreignCollections, a.otherNumbers);
            both.foreignCollections.addAll(b.foreignCollections);
            both.otherNumbers.addAll(b.otherNumbers);
            return both;
        }

        /**
         * @return whether these facts allow everything the other ones do
         */
        public boolean covers(final Facts other) {
            return other == null
                || foreignCollections.containsAll(other.foreignCollections) && otherNumbers.containsAll(other.otherNumbers);
        }

        /**
         * @return these facts about the given locals alone, any other local holding neither
         */
        public Facts only(final Set<String> javaVars) {
            final Facts kept = new Facts(foreignCollections, otherNumbers);
            kept.foreignCollections.retainAll(javaVars);
            kept.otherNumbers.retainAll(javaVars);
            return kept;
        }
    }

    /**
     * What a {@code segmentDef} holds in one generation of the rule: its type, its element type and the scope type of
     * the entity it holds, null for a value that is no entity.
     */
    @EqualsAndHashCode
    public static final class SegmentDefType {
        final Class<?> type;
        final Class<?> elementType;
        final String entityScope;

        public SegmentDefType(final Class<?> type, final Class<?> elementType, final String entityScope) {
            this.type = type;
            this.elementType = elementType;
            this.entityScope = entityScope;
        }
    }

    /**
     * A {@code build {}} block, as its run queues it.
     */
    public static final class BuildSite {
        public final TALScriptModel.BuildBlock block;
        /**
         * The {@code def} variables in scope where the block stands, loop variables included, in the order the queue
         * call captures their values.
         */
        public final Map<String, TALLocalVar> captured;
        /**
         * The scope type of each captured variable that holds an entity.
         */
        public final Map<String, String> capturedScopes;
        /**
         * What the variables captured by value hold where the block stands, which a later assignment does not change;
         * a {@code segmentDef} the block reads as it is when the block runs.
         */
        public final Facts capturedFacts;

        public BuildSite(final TALScriptModel.BuildBlock block, final Map<String, TALLocalVar> captured,
                         final Map<String, String> capturedScopes, final Facts capturedFacts) {
            this.block = block;
            this.captured = captured;
            this.capturedScopes = capturedScopes;
            this.capturedFacts = capturedFacts;
        }
    }
}
