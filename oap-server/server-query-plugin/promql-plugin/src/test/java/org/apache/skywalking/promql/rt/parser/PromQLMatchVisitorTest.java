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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.promql.rt.PromQLMatchVisitor;
import org.apache.skywalking.oap.query.promql.rt.result.MatcherSetResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PromQLMatchVisitorTest {
    @Test
    public void testMatchVisitor() {
        PromQLLexer lexer = new PromQLLexer(
            CharStreams.fromString("service_cpm{service='serviceA', layer='GENERAL'}"));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PromQLParser parser = new PromQLParser(tokens);
        ParseTree tree = parser.expression();
        PromQLMatchVisitor visitor = new PromQLMatchVisitor();
        MatcherSetResult parseResult = visitor.visit(tree);
        Assertions.assertEquals(ParseResultType.MATCH, parseResult.getResultType());
        Assertions.assertEquals("service_cpm", parseResult.getMetricName());
        Assertions.assertEquals(2, parseResult.getLabelMap().size());
    }
}
