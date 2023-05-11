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

package org.apache.skywalking.promql.rt.parser;

import java.util.Arrays;
import java.util.Collection;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.promql.rt.PromQLMatchVisitor;
import org.apache.skywalking.oap.query.promql.rt.result.MatcherSetResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class PromQLMatchVisitorTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "service_cpm",
                ParseResultType.MATCH,
                "service_cpm",
                0
            },
            {
                "service_cpm{}",
                ParseResultType.MATCH,
                "service_cpm",
                0
            },
            {
                "service_cpm{service='serviceA', layer='GENERAL'}",
                ParseResultType.MATCH,
                "service_cpm",
                2
            }
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testMatchVisitor(
        String expression,
        ParseResultType wantType,
        String metricName,
        int labelsSize) {
        PromQLLexer lexer = new PromQLLexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PromQLParser parser = new PromQLParser(tokens);
        ParseTree tree = parser.expression();
        PromQLMatchVisitor visitor = new PromQLMatchVisitor();
        MatcherSetResult parseResult = visitor.visit(tree);
        Assertions.assertEquals(wantType, parseResult.getResultType());
        Assertions.assertEquals(metricName, parseResult.getMetricName());
        Assertions.assertEquals(labelsSize, parseResult.getLabelMap().size());
    }
}
