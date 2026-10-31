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
import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;

/**
 * What TAL contributes to {@link TALValueCodegen}: the {@code span} and {@code meta} roots, the attribute
 * functions the semantic conventions are read through, and the lookups a peer's name goes through,
 * {@code component}, {@code callerScopedAddress}, {@code resolveNetworkAlias} and {@code isGateway}.
 *
 * <p>The entity constructors are function calls too, but they are not resolved here: only a {@code def} or a
 * {@code scope} may hold one, so {@link TALBlockCodegen} recognizes them by name where they are legal, and
 * anywhere else they fall through to the "no matching codegen path" error.
 */
public class TALSpanRootResolver implements TALRootResolver {
    /**
     * The attribute functions, each taking one or more keys and returning a string.
     */
    private static final List<String> STRING_FUNCTIONS = List.of("attr", "resource", "scopeAttr");

    /**
     * The most keys the runtime helper has a spelled-out overload for; {@code eventAttr} has one fewer, since its
     * first argument is the event name.
     */
    private static final int MAX_SPELLED_KEYS = 4;

    /**
     * The entity constructors, which only {@link TALBlockCodegen} may emit.
     */
    static final List<String> ENTITY_CONSTRUCTORS =
        List.of("service", "instance", "endpoint", "serviceRelation", "serviceInstanceRelation");

    /**
     * The rule's declared input type name, kept for diagnostics.
     */
    private final String inputTypeName;

    public TALSpanRootResolver(final Class<?> inputType) {
        this.inputTypeName = inputType == null ? "<none>" : inputType.getName();
    }

    @Override
    public boolean generateFunctionCall(final StringBuilder sb, final TALValueAccess value,
                                        final TALGenCtx genCtx) {
        final String name = value.getFunctionCallName();
        if (STRING_FUNCTIONS.contains(name)) {
            appendKeyedCall(sb, "h." + name, value, 1);
            return true;
        }
        if ("hasAttr".equals(name)) {
            appendKeyedCall(sb, "h.hasAttr", value, 1);
            return true;
        }
        if ("event".equals(name)) {
            requireKeys(value, name, 1, 1);
            sb.append("h.event(\"").append(literalArg(value, 0)).append("\")");
            return true;
        }
        if ("eventAttr".equals(name)) {
            requireKeys(value, name, 2, Integer.MAX_VALUE);
            final int count = value.getFunctionCallArgs().size();
            final boolean asArray = count > MAX_SPELLED_KEYS;
            sb.append("h.eventAttr(\"").append(literalArg(value, 0)).append("\"")
              .append(asArray ? ", new String[] {" : "");
            for (int i = 1; i < count; i++) {
                sb.append(i > 1 || !asArray ? ", " : "").append("\"").append(literalArg(value, i)).append("\"");
            }
            sb.append(asArray ? "})" : ")");
            return true;
        }
        if ("callerScopedAddress".equals(name)) {
            requireKeys(value, name, 2, 2);
            sb.append("h.callerScopedAddress(");
            TALValueCodegen.generateStringValueAccess(
                sb, value.getFunctionCallArgs().get(0).getValue(), null, genCtx);
            sb.append(", ");
            TALValueCodegen.generateStringValueAccess(
                sb, value.getFunctionCallArgs().get(1).getValue(), null, genCtx);
            sb.append(")");
            return true;
        }
        if ("findServerClusterName".equals(name)) {
            // The address, then optionally the kind a cluster lists in server-clusters.yml.
            requireKeys(value, name, 1, 2);
            sb.append("h.findServerClusterName(");
            for (int i = 0; i < value.getFunctionCallArgs().size(); i++) {
                sb.append(i > 0 ? ", " : "");
                TALValueCodegen.generateStringValueAccess(
                    sb, value.getFunctionCallArgs().get(i).getValue(), null, genCtx);
            }
            sb.append(")");
            return true;
        }
        if ("component".equals(name) || "resolveNetworkAlias".equals(name) || "urlHost".equals(name)
            || "isGateway".equals(name)) {
            requireKeys(value, name, 1, 1);
            sb.append("h.").append(name).append("(");
            TALValueCodegen.generateStringValueAccess(
                sb, value.getFunctionCallArgs().get(0).getValue(), null, genCtx);
            sb.append(")");
            return true;
        }
        return false;
    }

    /**
     * {@code meta.*} walks {@link SpanMetadata}'s getters, so a chain that names a field the class does not have
     * is a compile error rather than a null at runtime.
     */
    @Override
    public void generateMetaRootAccess(final StringBuilder sb,
                                       final List<? extends TALValueAccessSegment> chain,
                                       final TALGenCtx genCtx) {
        sb.append(chainAccess(chain, SpanMetadata.class, "h.ctx().getMeta()", false, genCtx, false));
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
        sb.append(chainAccess(chain, genCtx.inputType, "_p", true, genCtx, true));
    }

    @Override
    public ExprType functionReturnType(final String functionName) {
        if (STRING_FUNCTIONS.contains(functionName)) {
            return ExprType.STRING;
        }
        if ("hasAttr".equals(functionName) || "isGateway".equals(functionName)) {
            return ExprType.BOOLEAN;
        }
        if ("eventAttr".equals(functionName) || "resolveNetworkAlias".equals(functionName)
            || "callerScopedAddress".equals(functionName) || "urlHost".equals(functionName)
            || "findServerClusterName".equals(functionName)) {
            return ExprType.STRING;
        }
        if ("component".equals(functionName)) {
            return ExprType.INT;
        }
        return null;
    }

    /**
     * A span always arrives as a declared type, so a chain on the input root is always walkable by reflection.
     */
    @Override
    public boolean isTypedInputActive() {
        return true;
    }

    @Override
    public Class<?> metaRootType() {
        return SpanMetadata.class;
    }

    @Override
    public String describeContext() {
        return "inputType: " + inputTypeName;
    }

    /**
     * Walk the leading field segments against a typed root, then apply whatever method and index segments follow,
     * which is the shape every root chain takes. A root the rule can overwrite is read inline; see
     * {@link TALValueCodegen#generateInlineChainAccess}.
     */
    private static String chainAccess(final List<? extends TALValueAccessSegment> chain,
                                      final Class<?> rootType,
                                      final String rootExpr,
                                      final boolean rootCanBeNull,
                                      final TALGenCtx genCtx,
                                      final boolean cacheable) {
        final List<TALFieldSegment> fieldSegments = new ArrayList<>();
        int tailStart = -1;
        for (int i = 0; i < chain.size(); i++) {
            final TALValueAccessSegment seg = chain.get(i);
            if (seg instanceof TALFieldSegment) {
                fieldSegments.add((TALFieldSegment) seg);
            } else {
                tailStart = i;
                break;
            }
        }

        String current = cacheable
            ? TALValueCodegen.generateTypedChainAccess(fieldSegments, rootType, rootExpr, rootCanBeNull, genCtx)
            : TALValueCodegen.generateInlineChainAccess(fieldSegments, rootType, rootExpr, genCtx);

        if (tailStart >= 0) {
            for (int i = tailStart; i < chain.size(); i++) {
                final TALValueAccessSegment seg = chain.get(i);
                if (seg instanceof TALMethodSegment) {
                    current = TALValueCodegen.appendMethodSegment(current, (TALMethodSegment) seg, genCtx);
                } else if (seg instanceof TALIndexSegment) {
                    current = current + "[" + ((TALIndexSegment) seg).getIndex() + "]";
                } else if (seg instanceof TALFieldSegment) {
                    current = current + "." + ((TALFieldSegment) seg).getName();
                }
            }
        }
        return current;
    }

    /**
     * Emit a call whose arguments are all string-literal keys, which every attribute function takes.
     */
    private static void appendKeyedCall(final StringBuilder sb, final String method,
                                        final TALValueAccess value, final int minKeys) {
        requireKeys(value, value.getFunctionCallName(), minKeys, Integer.MAX_VALUE);
        final int count = value.getFunctionCallArgs().size();
        // Javassist does not adapt varargs: up to four keys reach a spelled-out overload, more go as the array
        // the varargs method takes.
        final boolean asArray = count > MAX_SPELLED_KEYS;
        sb.append(method).append(asArray ? "(new String[] {" : "(");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(literalArg(value, i)).append("\"");
        }
        sb.append(asArray ? "})" : ")");
    }

    private static String literalArg(final TALValueAccess value, final int index) {
        final TALFunctionArg arg = value.getFunctionCallArgs().get(index);
        if (!arg.getValue().isStringLiteral()) {
            throw new IllegalArgumentException(
                value.getFunctionCallName() + "() takes string literal arguments; got "
                    + arg.getValue().toPathString());
        }
        return DslJavaSourceText.toLiteral(arg.getValue().getSegments().get(0));
    }

    private static void requireKeys(final TALValueAccess value, final String name,
                                    final int min, final int max) {
        final int count = value.getFunctionCallArgs().size();
        if (count < min || count > max) {
            throw new IllegalArgumentException(
                name + "() expects " + (min == max ? String.valueOf(min) : "at least " + min)
                    + " argument(s), got " + count);
        }
    }
}
