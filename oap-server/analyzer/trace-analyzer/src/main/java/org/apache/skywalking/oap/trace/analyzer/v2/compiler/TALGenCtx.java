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
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;

/**
 * The state one rule's code generation carries: the resolved types, the local and cached-chain variables, where
 * in the rule the generator is, and the channel {@link TALRootResolver#generateInputRootAccess} uses to report the
 * static type and null checks of the chain it just emitted.
 *
 * <p>Shared by every DSL; what differs between them lives behind {@link #resolver}.
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
    public String lastNullChecks;
    public String lastRawChain;

    /**
     * Per-method chain variable caching: chain key to variable name, so the same chain accessed twice reuses one
     * variable.
     */
    public final Map<String, String> protoVars = new HashMap<>();
    public final List<String[]> protoLvtVars = new ArrayList<>();
    public final StringBuilder protoVarDecls = new StringBuilder();
    public int protoVarCounter;
    public boolean usedProtoAccess;

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
     * What each {@code segmentDef} holds in this generation of the rule, as its type, its element type and the scope
     * type of the entity it holds, null for a value that is no entity; a name not settled yet holds an {@code Object}.
     * {@code TALClassGenerator.compile} generates the rule until these match {@link #learnedSegmentDefTypes}, since a
     * variable can be read before the statement that types it.
     */
    public Map<String, List<Object>> segmentDefTypes = Collections.emptyMap();
    /**
     * What the first generated {@code segmentDef} statement of each name gave its value in this generation.
     */
    public final Map<String, List<Object>> learnedSegmentDefTypes = new HashMap<>();
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
        lastNullChecks = null;
        lastRawChain = null;
    }

    /**
     * Start a generated method: its own locals and chain caches, at the filter level, with every {@code segmentDef}
     * read from the segment's array into a local of its type.
     */
    public void beginMethod() {
        resetProtoVars();
        entityScopeTypes.clear();
        extractorDepth = 0;
        voteDepth = 0;
        buildDepth = 0;
        for (final Map.Entry<String, Integer> slot : segmentDefSlots.entrySet()) {
            final List<Object> type = segmentDefTypes.get(slot.getKey());
            final Class<?> resolved = type == null ? Object.class : (Class<?>) type.get(0);
            final String javaVar = "_sd_" + slot.getKey();
            final String typeName = TALCodegenHelper.javaTypeName(resolved);
            declaredJavaNames.add(javaVar);
            localVars.put(slot.getKey(),
                new TALLocalVar(javaVar, resolved, type == null ? null : (Class<?>) type.get(1), slot.getValue()));
            if (type != null && type.get(2) != null) {
                entityScopeTypes.put(slot.getKey(), (String) type.get(2));
            }
            localVarDecls.append("  ").append(typeName).append(" ").append(javaVar).append(" = (").append(typeName)
                         .append(") ctx.getSegmentDefs()[").append(slot.getValue()).append("];\n");
            localVarLvtVars.add(new String[] {javaVar, TALCodegenHelper.descriptor(resolved)});
        }
    }

    public void resetProtoVars() {
        protoVars.clear();
        protoLvtVars.clear();
        protoVarDecls.setLength(0);
        protoVarCounter = 0;
        usedProtoAccess = false;
        localVars.clear();
        localVarDecls.setLength(0);
        localVarLvtVars.clear();
        blockScopes.clear();
        declaredJavaNames.clear();
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

        public BuildSite(final TALScriptModel.BuildBlock block, final Map<String, TALLocalVar> captured,
                         final Map<String, String> capturedScopes) {
            this.block = block;
            this.captured = captured;
            this.capturedScopes = capturedScopes;
        }
    }
}
