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

package org.apache.skywalking.oap.query.graphql.mqe.rt;

import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.mqe.rt.exception.ParseErrorListener;
import org.apache.skywalking.mqe.rt.grammar.MQELexer;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResultType;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.core.storage.annotation.InspectQueryContext;
import org.apache.skywalking.oap.server.core.storage.annotation.ForeignMetricMeta;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

/**
 * Synchronous MQE evaluation entry, factored out of {@code MetricsExpressionQuery} so the admin
 * inspect value path can run the SAME engine without the resolver's {@code queryAsync} ForkJoinPool
 * hop. Optionally overlays caller-supplied metadata for foreign metrics (metrics this OAP does not
 * define) via {@link InspectQueryContext} for the duration of the synchronous eval; the overlay is
 * provide-if-absent and removed in a {@code finally} on the same thread that runs the eval and the
 * storage read (the eval is fully synchronous, mirroring how {@code TRACE_CONTEXT} already rides that
 * thread into the DAO).
 */
public class MQEExecutor {
    private final ModuleManager moduleManager;
    private final DecimalFormat valueFormat = new DecimalFormat();

    public MQEExecutor(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.valueFormat.setGroupingUsed(false);
    }

    /**
     * Evaluate an MQE expression synchronously.
     *
     * @param expression the MQE expression
     * @param entity     the query entity; its scope is used to bind any foreign metric
     * @param duration   the query time range
     * @param foreign    metadata for foreign metrics referenced by the expression; {@code null} or
     *                   empty for a purely catalog query (the public GraphQL path passes null)
     * @return the native expression result
     */
    public ExpressionResult execute(final String expression, final Entity entity, final Duration duration,
                                    final List<ForeignMetricMeta> foreign) {
        final boolean hasForeign = foreign != null && !foreign.isEmpty();
        if (hasForeign) {
            InspectQueryContext.set(foreign.stream().collect(
                Collectors.toMap(ForeignMetricMeta::getMetricName, Function.identity(), (a, b) -> a)));
        }
        final DebuggingTraceContext traceContext = new DebuggingTraceContext(
            "Inspect MQE: " + expression + ", Entity: " + entity + ", Duration: " + duration, false, false);
        TRACE_CONTEXT.set(traceContext);
        try {
            final MQEVisitor visitor = new MQEVisitor(moduleManager, entity, duration);
            final MQELexer lexer = new MQELexer(CharStreams.fromString(expression));
            lexer.addErrorListener(new ParseErrorListener());
            final MQEParser parser = new MQEParser(new CommonTokenStream(lexer));
            parser.addErrorListener(new ParseErrorListener());
            final ParseTree tree;
            try {
                tree = parser.expression();
            } catch (ParseCancellationException e) {
                final ExpressionResult errorResult = new ExpressionResult();
                errorResult.setType(ExpressionResultType.UNKNOWN);
                errorResult.setError(e.getMessage());
                return errorResult;
            }
            final ExpressionResult result = visitor.visit(tree);
            result.getResults().forEach(mqeValues -> mqeValues.getValues().forEach(mqeValue -> {
                if (!mqeValue.isEmptyValue()) {
                    mqeValue.setValue(valueFormat.format(mqeValue.getDoubleValue()));
                }
            }));
            return result;
        } finally {
            traceContext.stopTrace();
            TRACE_CONTEXT.remove();
            if (hasForeign) {
                InspectQueryContext.clear();
            }
        }
    }
}
