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

/**
 * Binary arithmetic or concatenation operator joining adjacent operands of a value expression. For N operands
 * there are N-1 operators, {@code ops[i]} joining {@code parts[i]} and {@code parts[i + 1]}. Precedence is already
 * encoded by the parser, which nests multiplicative expressions inside additive operands. The symbol is the Java
 * source token, so the codegen needs no parallel switch.
 */
public enum BinaryOp {
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    SLASH("/");

    private final String symbol;

    BinaryOp(final String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
