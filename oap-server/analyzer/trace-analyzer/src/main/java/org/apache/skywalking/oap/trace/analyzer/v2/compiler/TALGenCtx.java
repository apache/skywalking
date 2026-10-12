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
 * The state one rule's code generation carries: the resolved types, the private methods emitted so far, the local
 * and cached-chain variables, and the channel {@link TALRootResolver#generateInputRootAccess} uses to report the
 * static type and null checks of the chain it just emitted.
 *
 * <p>Shared by every DSL; what differs between them lives behind {@link #resolver}.
 */
public class TALGenCtx {
    public final TALRootResolver resolver;
    public final Class<?> inputType;
    public final Class<?> outputType;
    public final List<TALPrivateMethod> privateMethods = new ArrayList<>();
    private final Map<String, Integer> methodCounts = new HashMap<>();

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

    public String nextMethodName(final String prefix) {
        final int count = methodCounts.merge(prefix, 1, Integer::sum);
        return count == 1 ? "_" + prefix : "_" + prefix + "_" + count;
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

    public Object[] saveProtoVarState() {
        return new Object[] {
            new HashMap<>(protoVars),
            new ArrayList<>(protoLvtVars),
            protoVarDecls.toString(),
            protoVarCounter,
            usedProtoAccess,
            new HashMap<>(localVars),
            localVarDecls.toString(),
            new ArrayList<>(localVarLvtVars)
        };
    }

    @SuppressWarnings("unchecked")
    public void restoreProtoVarState(final Object[] state) {
        protoVars.clear();
        protoVars.putAll((Map<String, String>) state[0]);
        protoLvtVars.clear();
        protoLvtVars.addAll((List<String[]>) state[1]);
        protoVarDecls.setLength(0);
        protoVarDecls.append((String) state[2]);
        protoVarCounter = (Integer) state[3];
        usedProtoAccess = (Boolean) state[4];
        localVars.clear();
        localVars.putAll((Map<String, TALLocalVar>) state[5]);
        localVarDecls.setLength(0);
        localVarDecls.append((String) state[6]);
        localVarLvtVars.clear();
        localVarLvtVars.addAll((List<String[]>) state[7]);
    }
}
