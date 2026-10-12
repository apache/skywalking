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
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;

/**
 * Everything the shared expression codegen cannot know: a DSL's roots and its built-in functions.
 *
 * <p>{@link TALValueCodegen} owns arithmetic, comparison, casts, literals, safe navigation, {@code def} variables
 * and reflective getter chains, all of which read the same in every DSL. What differs is the handful of names a
 * rule starts an expression with, and that is what this supplies: the {@code span} and {@code meta} roots,
 * {@code attr()} and the rest of TAL's span vocabulary, and the entity constructors.
 */
public interface TALRootResolver {
    /**
     * Emit a call to one of this DSL's built-in functions.
     *
     * @param sb    the buffer the generated Java source is appended to
     * @param value the expression, whose {@code functionCallName} names the built-in
     * @param ctx   the generation context
     * @return true when the name was one of this DSL's built-ins and code was emitted, false to let the shared
     * codegen continue looking
     */
    boolean generateFunctionCall(StringBuilder sb, TALValueAccess value, TALGenCtx ctx);

    /**
     * Emit a reference to a static helper the language exposes. TAL has none today; the hook exists because the
     * expression engine dispatches on it.
     *
     * @param sb    the buffer the generated Java source is appended to
     * @param value the expression, for which {@link TALValueAccess#isStaticRef()} is true
     * @param ctx   the generation context
     * @return true when code was emitted
     */
    default boolean generateStaticRefAccess(final StringBuilder sb, final TALValueAccess value,
                                            final TALGenCtx ctx) {
        return false;
    }

    /**
     * Emit a chain rooted at the metadata root, {@code meta.*}.
     *
     * @param sb    the buffer the generated Java source is appended to
     * @param chain the accesses applied to the root
     * @param ctx   the generation context
     */
    void generateMetaRootAccess(StringBuilder sb, List<? extends TALValueAccessSegment> chain, TALGenCtx ctx);

    /**
     * Emit a chain rooted at the input root, {@code span.*}, by walking it reflectively through
     * {@link TALValueCodegen#generateTypedChainAccess} against the rule's declared input type.
     *
     * @param sb    the buffer the generated Java source is appended to
     * @param chain the accesses applied to the root
     * @param ctx   the generation context
     */
    void generateInputRootAccess(StringBuilder sb, List<? extends TALValueAccessSegment> chain, TALGenCtx ctx);

    /**
     * @param functionName the built-in being called
     * @return the type that built-in returns, so arithmetic and comparisons around it are typed rather than
     * falling back to string concatenation; null when the DSL does not know the name
     */
    default ExprType functionReturnType(final String functionName) {
        return null;
    }

    /**
     * @return whether the input root resolves against {@link TALGenCtx#inputType}, so a chain on it can be walked
     * by reflection at compile time. A span always has a declared input type, so this is true whenever the rule
     * compiled with one.
     */
    boolean isTypedInputActive();

    /**
     * @return a short description of the compile context, such as the active parser and input type, quoted in the
     * diagnostic raised when no codegen path matches an expression
     */
    String describeContext();
}
