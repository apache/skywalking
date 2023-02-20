package org.apache.skywalking.prometheus.rt.parser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.prometheus.rt.PromQLMatchVisitor;
import org.apache.skywalking.oap.query.prometheus.rt.result.MatcherSetResult;
import org.apache.skywalking.oap.query.prometheus.rt.result.ParseResultType;
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
            Assertions.assertEquals(ParseResultType.match, parseResult.getResultType());
            Assertions.assertEquals("service_cpm", parseResult.getMetricName());
            Assertions.assertEquals(2, parseResult.getLabelMap().size());
        }
}
