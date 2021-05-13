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
 *
 */

package org.apache.skywalking.oap.server.core.alarm.provider.expression;

import lombok.extern.slf4j.Slf4j;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Expression support eval java basic expressions, just like groovy script
 * The internal detail is it first compile the expression to a parseTree then execute the parseTree with data
 * It caches the compiled expression for sake of performance
 */
@Slf4j
public class Expression {
    private final Map<String, Object> expressionCache;
    private final ExpressionContext context;

    public Expression(ExpressionContext context) {
        this.context = context;
        this.expressionCache = new ConcurrentHashMap<>();
    }

    /**
     * Eval the given expression using empty data context
     */
    public Object eval(String expression) {
        return eval(expression, null);
    }

    /**
     * Eval the given expression with data context
     */
    public Object eval(String expression, Map<String, Object> vars) {
        try {
            Object obj = compile(expression, context);
            return MVEL.executeExpression(obj, vars);
        } catch (Throwable e) {
            log.error("eval expression {} error", expression, e);
            return null;
        }
    }

    /**
     * Compile the given expression to a parseTree
     */
    public Object compile(String expression, ExpressionContext pctx) {
        return expressionCache.computeIfAbsent(expression, s -> MVEL.compileExpression(expression, pctx.getContext()));
    }

    /**
     * Analysis expression dependencies
     */
    public Set<String> analysisInputs(String expression) {
        ParserContext pCtx = ParserContext.create();
        MVEL.analysisCompile(expression, pCtx);
        Map<String, Class> inputsMap = pCtx.getInputs();
        return inputsMap.keySet();
    }
}
