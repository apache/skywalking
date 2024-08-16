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

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.concurrent.CompletableFuture;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import java.text.DecimalFormat;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.graphql.mqe.rt.MQEVisitor;
import org.apache.skywalking.mqe.rt.exception.ParseErrorListener;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTrace;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.mqe.rt.grammar.MQELexer;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;

import static org.apache.skywalking.oap.query.graphql.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class MetricsExpressionQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private final DecimalFormat valueFormat = new DecimalFormat();

    public MetricsExpressionQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.valueFormat.setGroupingUsed(false);
    }

    public CompletableFuture<ExpressionResult> execExpression(String expression,
                                                              Entity entity,
                                                              Duration duration,
                                                              boolean debug,
                                                              boolean dumpStorageRsp) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "Expression: " + expression + ", Entity: " + entity + ", Duration: " + duration, debug, dumpStorageRsp);
            TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("MQE query");
            try {
                MQEVisitor visitor = new MQEVisitor(moduleManager, entity, duration);
                DebuggingTrace execTrace = traceContext.getExecTrace();
                DebuggingSpan syntaxSpan = traceContext.createSpan("MQE syntax analysis");
                ParseTree tree;
                try {
                    MQELexer lexer = new MQELexer(
                        CharStreams.fromString(expression));
                    lexer.addErrorListener(new ParseErrorListener());
                    MQEParser parser = new MQEParser(new CommonTokenStream(lexer));
                    parser.addErrorListener(new ParseErrorListener());
                    try {
                        tree = parser.expression();
                    } catch (ParseCancellationException e) {
                        ExpressionResult errorResult = new ExpressionResult();
                        errorResult.setType(ExpressionResultType.UNKNOWN);
                        errorResult.setError(e.getMessage());
                        return errorResult;
                    }
                } finally {
                    traceContext.stopSpan(syntaxSpan);
                }
                ExpressionResult parseResult = visitor.visit(tree);
                parseResult.getResults().forEach(mqeValues -> {
                    mqeValues.getValues().forEach(mqeValue -> {
                        if (!mqeValue.isEmptyValue()) {
                            mqeValue.setValue(valueFormat.format(mqeValue.getDoubleValue()));
                        }
                    });
                });
                if (debug) {
                    parseResult.setDebuggingTrace(execTrace);
                }
                return parseResult;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }
}
