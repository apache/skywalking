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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.mqe.rt.exception.ParseErrorListener;
import org.apache.skywalking.mqe.rt.grammar.MQELexer;
import org.apache.skywalking.mqe.rt.grammar.MQEParser;
import org.apache.skywalking.mqe.rt.type.ExpressionResult;
import org.apache.skywalking.mqe.rt.type.ExpressionResultType;
import org.apache.skywalking.oap.server.core.alarm.provider.expr.rt.AlarmMQEVerifyVisitor;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Data
@ToString
@EqualsAndHashCode
public class AlarmRule {
    private String alarmRuleName;
    private String expression;
    @Setter(AccessLevel.NONE)
    private Set<String> includeMetrics;
    private ArrayList<String> includeNames;
    private String includeNamesRegex;
    private ArrayList<String> excludeNames;
    private String excludeNamesRegex;
    private int period;
    private int silencePeriod;
    private String message;
    private Map<String, String> tags;
    private Set<String> hooks;

    /**
     * Init includeMetrics and verify the expression.
     * ValueColumnMetadata need init metrics info, don't invoke before the module finishes start.
     */
    public void setExpression(final String expression) throws IllegalExpressionException {
        MQELexer lexer = new MQELexer(CharStreams.fromString(expression));
        lexer.addErrorListener(new ParseErrorListener());
        MQEParser parser = new MQEParser(new CommonTokenStream(lexer));
        ParseTree tree;
        try {
            tree = parser.expression();
        } catch (ParseCancellationException e) {
            throw new IllegalExpressionException("Expression: " + expression + " error: " + e.getMessage());
        }
        AlarmMQEVerifyVisitor visitor = new AlarmMQEVerifyVisitor();
        ExpressionResult parseResult = visitor.visit(tree);
        if (StringUtil.isNotBlank(parseResult.getError())) {
            throw new IllegalExpressionException("Expression: " + expression + " error: " + parseResult.getError());
        }
        if (!parseResult.isBoolResult()) {
            throw new IllegalExpressionException("Expression: " + expression + " root operation is not a Compare Operation.");
        }
        if (ExpressionResultType.SINGLE_VALUE != parseResult.getType()) {
            throw new IllegalExpressionException("Expression: " + expression + " is not a SINGLE_VALUE result expression.");
        }

        verifyIncludeMetrics(visitor.getIncludeMetrics(), expression);
        this.expression = expression;
        this.includeMetrics = visitor.getIncludeMetrics();
    }

    private void verifyIncludeMetrics(Set<String> includeMetrics, String expression) throws IllegalExpressionException {
        Set<String> scopeSet = new HashSet<>();
        for (String metricName : includeMetrics) {
            Optional<ValueColumnMetadata.ValueColumn> valueColumn = ValueColumnMetadata.INSTANCE.readValueColumnDefinition(
                metricName);
            if (valueColumn.isEmpty()) {
                throw new IllegalExpressionException("Metric: [" + metricName + "] dose not exist.");
            }
            scopeSet.add(ValueColumnMetadata.INSTANCE.getScope(metricName).name());
            Column.ValueDataType dataType = valueColumn.get().getDataType();
            switch (dataType) {
                case COMMON_VALUE:
                case LABELED_VALUE:
                    break;
                default:
                    throw new IllegalExpressionException(
                        "Metric dose not supported in alarm, metric: [" + metricName + "] is not a common or labeled metric.");
            }
        }
        if (scopeSet.size() != 1) {
            throw new IllegalExpressionException(
                "The metrics in expression: " + expression + " must have the same scope level, but got: " + scopeSet);
        }
    }
}
