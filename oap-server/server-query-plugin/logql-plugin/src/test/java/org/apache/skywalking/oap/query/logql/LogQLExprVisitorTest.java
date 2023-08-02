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

package org.apache.skywalking.oap.query.logql;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.logql.rt.grammar.LogQLLexer;
import org.apache.skywalking.logql.rt.grammar.LogQLParser;
import org.apache.skywalking.oap.query.logql.entity.LabelName;
import org.apache.skywalking.oap.query.logql.rt.LogQLExprVisitor;
import org.apache.skywalking.oap.query.logql.rt.exception.ParseErrorListener;
import org.apache.skywalking.oap.query.logql.rt.result.LogQLParseResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogQLExprVisitorTest {

    @Test
    public void testStreamSelector() {
        String expression = "{service=\"test-service\", service_instance=\"test-instance\", trace_id=\" \"}";
        LogQLParseResult parseResult = parseLogQL(expression);

        Assertions.assertEquals("test-service", parseResult.getLabelMap().get(LabelName.SERVICE.getLabel()));
        Assertions.assertEquals("test-instance", parseResult.getLabelMap().get(LabelName.SERVICE_INSTANCE.getLabel()));
        Assertions.assertNull(parseResult.getLabelMap().get(LabelName.TRACE_ID.getLabel()));
        Assertions.assertEquals(parseResult.getKeywordsOfContent().size(), 0);
        Assertions.assertEquals(parseResult.getExcludingKeywordsOfContent().size(), 0);
    }

    @Test
    public void testStreamSelectorWithLabelFilter() {
        String expression = "{service=\"test-service\"} |=`contains` !=`not_contains`";
        LogQLParseResult parseResult = parseLogQL(expression);

        Assertions.assertEquals("test-service", parseResult.getLabelMap().get(LabelName.SERVICE.getLabel()));
        Assertions.assertEquals(parseResult.getKeywordsOfContent().get(0), "contains");
        Assertions.assertEquals(parseResult.getExcludingKeywordsOfContent().get(0), "not_contains");
    }

    @Test
    public void testStreamSelectorWithLabelFilterInMultipleLines() {
        String expression = "{service=\"test-service\"} \n" +
            "|=`contains` !=`not_contains`";
        LogQLParseResult parseResult = parseLogQL(expression);

        Assertions.assertEquals("test-service", parseResult.getLabelMap().get(LabelName.SERVICE.getLabel()));
        Assertions.assertEquals(parseResult.getKeywordsOfContent().get(0), "contains");
        Assertions.assertEquals(parseResult.getExcludingKeywordsOfContent().get(0), "not_contains");
    }

    @Test
    public void testIllegalExpression() {
        String expression1 = "prefix{service=\"test-service\"}";
        Assertions.assertThrowsExactly(ParseCancellationException.class, () -> parseLogQL(expression1));

        String expression2 = "{service=\"test-service\"}postfix";
        Assertions.assertThrowsExactly(ParseCancellationException.class, () -> parseLogQL(expression2));

        String expression3 = "{service=\"test-service\"} bad-op `test`";
        Assertions.assertThrowsExactly(ParseCancellationException.class, () -> parseLogQL(expression3));
    }

    private LogQLParseResult parseLogQL(String expression) {
        LogQLLexer lexer = new LogQLLexer(CharStreams.fromString(expression));
        lexer.addErrorListener(new ParseErrorListener());
        LogQLParser parser = new LogQLParser(new CommonTokenStream(lexer));
        parser.addErrorListener(new ParseErrorListener());
        ParseTree tree;
        tree = parser.root();
        LogQLExprVisitor visitor = new LogQLExprVisitor();
        return visitor.visit(tree);
    }
}
