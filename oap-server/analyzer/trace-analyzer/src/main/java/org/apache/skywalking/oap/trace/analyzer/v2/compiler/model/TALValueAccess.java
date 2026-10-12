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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.model;

import java.util.List;

/**
 * One value expression of a DSL rule, as the shared expression codegen sees it.
 *
 * <p>TAL's parse tree implements this view over its own classes, so the arithmetic, comparison, cast, literal
 * and chain codegen is written against the interface and the span-specific root and function handling goes
 * through {@link org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALRootResolver}.
 */
public interface TALValueAccess {
    /**
     * @return the dotted path this expression names, the first element being the root or the identifier
     */
    List<String> getSegments();

    /**
     * @return whether this reads the rule's typed input root, {@code span}
     */
    boolean isInputRef();

    /**
     * @return whether this reads the rule's metadata root, {@code meta}
     */
    boolean isMetaRef();

    /**
     * @return whether this is a static helper call the language resolves itself; TAL has none today
     */
    boolean isStaticRef();

    boolean isStringLiteral();

    boolean isNumberLiteral();

    /**
     * @return the field, method and index accesses applied to the root, in order
     */
    List<? extends TALValueAccessSegment> getChain();

    /**
     * @return the name of the built-in function this expression calls, or null when it calls none
     */
    String getFunctionCallName();

    List<? extends TALFunctionArg> getFunctionCallArgs();

    /**
     * @return the operands of a binary expression, empty when this is not one; {@link #getConcatOps()} holds the
     * N-1 operators that join them
     */
    List<? extends TALValueAccess> getConcatParts();

    List<BinaryOp> getConcatOps();

    /**
     * @return the expression inside a parenthesized cast, {@code (inner as Type).chain}, or null
     */
    TALValueAccess getParenInner();

    String getParenCast();

    /**
     * @return the dotted path as text, for diagnostics
     */
    String toPathString();
}
