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

import java.util.List;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;

/**
 * What TAL contributes to {@link TALValueCodegen}: the {@code span} and {@code meta} roots, and every function call,
 * resolved through the {@link TALFunctionRegistry}: an input's own functions, the OTLP span's and the native span's,
 * the helper functions its provider hands over, and those modules contribute.
 *
 * <p>The entity constructors are function calls too, but they are not resolved here: only a {@code def} or a
 * {@code scope} may hold one, so {@link TALBlockCodegen} recognizes them by name where they are legal, and
 * anywhere else they fall through to the "no matching codegen path" error.
 */
public class TALSpanRootResolver implements TALRootResolver {
    /**
     * The entity constructors, which only {@link TALBlockCodegen} may emit.
     */
    static final List<String> ENTITY_CONSTRUCTORS =
        List.of("service", "instance", "endpoint", "serviceRelation", "serviceInstanceRelation");

    /**
     * The rule's declared input type name, kept for diagnostics.
     */
    private final String inputTypeName;

    /**
     * The contributed functions, and the alias of the rule's input they are resolved for.
     */
    private final TALFunctionRegistry functions;
    private final String inputAlias;

    public TALSpanRootResolver(final Class<?> inputType) {
        this(inputType, null, null);
    }

    public TALSpanRootResolver(final Class<?> inputType, final TALFunctionRegistry functions,
                               final String inputAlias) {
        this.inputTypeName = inputType == null ? "<none>" : inputType.getName();
        this.functions = functions == null ? TALFunctionRegistry.defaults() : functions;
        this.inputAlias = inputAlias;
    }

    /**
     * Every function a rule calls is one the registry binds: the input's own, its provider's helpers, and the modules'.
     */
    @Override
    public boolean generateFunctionCall(final StringBuilder sb, final TALValueAccess value,
                                        final TALGenCtx genCtx) {
        final TALFunctionRegistry.Resolved contributed = contributed(value, genCtx);
        if (contributed != null) {
            generateContributedCall(sb, contributed, value, genCtx);
            return true;
        }
        return false;
    }

    /**
     * {@code _fnN.name(h, args...)}: the bound function object, looked up once per generated method, the run's
     * context first when the method takes it, each argument converted to its parameter's type, and the arguments a
     * variable-length tail takes passed as the array Java hands it, since Javassist does not adapt varargs.
     */
    private static void generateContributedCall(final StringBuilder sb,
                                                final TALFunctionRegistry.Resolved fn,
                                                final TALValueAccess value,
                                                final TALGenCtx genCtx) {
        // A byte or short result is widened here, so the call is the int the rule reads it as.
        final boolean narrow = isNarrowInteger(fn.getMethod().getReturnType());
        sb.append(narrow ? "((int) " : "")
          .append(genCtx.functionObjectVar(fn.getOwnerKey(), fn.getOwner().getClass().getName())).append(".")
          .append(fn.getMethod().getName()).append("(");
        final List<? extends TALFunctionArg> args = value.getFunctionCallArgs();
        final int fixed = fn.ruleArgumentCount();
        boolean first = true;
        if (fn.isTakesContext()) {
            sb.append("h");
            first = false;
        }
        for (int i = 0; i < args.size(); i++) {
            if (i == fixed && fn.isVarArgs()) {
                sb.append(first ? "" : ", ").append("new ")
                  .append(TALCodegenHelper.javaTypeName(fn.parameterFor(i))).append("[] {");
                first = true;
            } else if (!first) {
                sb.append(", ");
            }
            first = false;
            final TALFunctionArg arg = args.get(i);
            TALValueCodegen.generateArgumentFor(sb, arg.getValue(), arg.getCastType(), fn.parameterFor(i),
                "argument " + (i + 1) + " of " + fn.getMethod().getName() + "()", genCtx);
        }
        if (fn.isVarArgs()) {
            if (args.size() == fixed) {
                sb.append(first ? "" : ", ").append("new ")
                  .append(TALCodegenHelper.javaTypeName(fn.parameterFor(fixed))).append("[0]");
            } else {
                sb.append("}");
            }
        }
        sb.append(narrow ? "))" : ")");
    }

    private static boolean isNarrowInteger(final Class<?> type) {
        return type == short.class || type == byte.class;
    }

    /**
     * The overload a contributed call resolves to, chosen once per call site so its code and its typing agree: by
     * the number of arguments, then, among overloads of that arity, by the arguments' compile-time types.
     *
     * @return the overload, or null when no bound provider serves the name for the rule's input
     */
    private TALFunctionRegistry.Resolved contributed(final TALValueAccess call, final TALGenCtx genCtx) {
        final TALFunctionRegistry.Resolved known = genCtx.resolvedCalls.get(call);
        if (known != null) {
            return known;
        }
        final String name = call.getFunctionCallName();
        final List<TALFunctionRegistry.Resolved> candidates =
            functions.candidates(name, call.getFunctionCallArgs().size(), inputAlias);
        if (candidates == null) {
            return null;
        }
        final TALFunctionRegistry.Resolved chosen = candidates.size() == 1 ? candidates.get(0)
            : TALFunctionRegistry.select(name, candidates,
                TALValueCodegen.argumentTypes(call.getFunctionCallArgs(), genCtx));
        genCtx.resolvedCalls.put(call, chosen);
        return chosen;
    }

    /**
     * {@code meta.*} walks {@link SpanMetadata}'s getters, so a chain that names a field the class does not have
     * is a compile error rather than a null at runtime. The root is never null.
     */
    @Override
    public void generateMetaRootAccess(final StringBuilder sb,
                                       final List<? extends TALValueAccessSegment> chain,
                                       final TALGenCtx genCtx) {
        TALDefCodegen.generateDefVarChain(sb,
            new TALDefCodegen.ChainReceiver("h.ctx().getMeta()", SpanMetadata.class, null, null, false), chain, genCtx);
    }

    /**
     * {@code span.*} walks the rule's declared input type from the wire object.
     */
    @Override
    public void generateInputRootAccess(final StringBuilder sb,
                                        final List<? extends TALValueAccessSegment> chain,
                                        final TALGenCtx genCtx) {
        if (genCtx.inputType == null) {
            throw new IllegalArgumentException(
                "span.* is only resolvable when the rule declares a single inputType; " + describeContext());
        }
        genCtx.usesInput = true;
        TALDefCodegen.generateDefVarChain(sb, new TALDefCodegen.ChainReceiver("_p", genCtx.inputType, null, null, true),
            chain, genCtx);
    }

    @Override
    public ExprType functionReturnType(final TALValueAccess call, final TALGenCtx genCtx) {
        if (ENTITY_CONSTRUCTORS.contains(call.getFunctionCallName())) {
            return null;
        }
        final TALFunctionRegistry.Resolved contributed = contributed(call, genCtx);
        if (contributed == null) {
            return null;
        }
        final Class<?> returns = contributed.getMethod().getReturnType();
        return returns == String.class ? ExprType.STRING
            : returns == long.class ? ExprType.LONG
            : returns == int.class || isNarrowInteger(returns) ? ExprType.INT
            : returns == double.class ? ExprType.DOUBLE
            : returns == float.class ? ExprType.FLOAT
            : returns == boolean.class ? ExprType.BOOLEAN
            : ExprType.OBJECT;
    }

    @Override
    public Class<?> functionReturnClass(final TALValueAccess call, final TALGenCtx genCtx) {
        if (ENTITY_CONSTRUCTORS.contains(call.getFunctionCallName())) {
            return null;
        }
        final TALFunctionRegistry.Resolved contributed = contributed(call, genCtx);
        return contributed == null ? null : contributed.getMethod().getReturnType();
    }

    @Override
    public Class<?> functionReturnElementType(final TALValueAccess call, final TALGenCtx genCtx) {
        if (ENTITY_CONSTRUCTORS.contains(call.getFunctionCallName())) {
            return null;
        }
        final TALFunctionRegistry.Resolved contributed = contributed(call, genCtx);
        return contributed == null ? null
            : TALCodegenHelper.listElementType(contributed.getMethod().getGenericReturnType());
    }

    /**
     * A span always arrives as a declared type, so a chain on the input root is always walkable by reflection.
     */
    @Override
    public boolean isTypedInputActive() {
        return true;
    }

    @Override
    public String describeContext() {
        return "inputType: " + inputTypeName;
    }
}
