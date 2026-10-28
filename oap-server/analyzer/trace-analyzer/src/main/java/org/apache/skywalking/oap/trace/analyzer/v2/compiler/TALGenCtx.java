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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public final Class<?> outputType;

    /**
     * Rule's name, emitted as the first argument of every probe call site.
     */
    public String ruleName = "";

    /**
     * Set by the input-root codegen so a caller can specialize on a primitive leaf; cleared at the start of every
     * value access.
     */
    public Class<?> lastResolvedType;
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
     * The prefix every metric of this rule's file carries, from the file's {@code metricPrefix}.
     */
    public String metricPrefix;

    /**
     * Every metric the rule declares, including those inside {@code if} branches, collected so
     * {@code MeterSystem.create} runs for each before the class is loaded.
     */
    public final List<TALMetricDeclaration> metricDeclarations = new ArrayList<>();

    /**
     * {@code def} variables that hold an entity, mapped to the {@code ScopeType} their constructor implies, so a
     * {@code scope} naming one registers its metric under the right scope.
     */
    public final Map<String, String> entityScopeTypes = new HashMap<>();

    /**
     * How deep the generator is inside an {@code extractor} and inside a {@code sink} or {@code persist} block,
     * so a block that belongs at the filter level is refused anywhere else.
     */
    public int extractorDepth;
    public int voteDepth;

    /**
     * The {@code sink} blocks generated, since a rule with an {@code outputType} needs one to write its record.
     */
    public int sinkCount;

    /**
     * The rule's {@code buildNetworkAlias}, whose address expression becomes the generated
     * {@code networkAliasAddress} method; null when the rule builds no alias.
     */
    public TALScriptModel.NetworkAliasStatement networkAliasBuild;

    private int localNameCounter;

    public TALGenCtx(final TALRootResolver resolver, final Class<?> inputType, final Class<?> outputType) {
        this.resolver = resolver;
        this.inputType = inputType;
        this.outputType = outputType;
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
        lastNullChecks = null;
        lastRawChain = null;
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
    }
}
