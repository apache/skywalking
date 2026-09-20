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

package org.apache.skywalking.oap.query.traceql.rt;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.tempo.grammar.TraceQLLexer;
import org.apache.skywalking.oap.query.tempo.grammar.TraceQLParser;

/**
 * TraceQL query parser utility.
 * Parses TraceQL queries and converts them to QueryRequest parameters.
 */
public class TraceQLQueryParser {

    /**
     * Parse a TraceQL query string and return the parse tree.
     *
     * @param query TraceQL query string (e.g., "{.service.name=\"frontend\"}")
     * @return Parse tree root
     */
    public static ParseTree parse(String query) {
        CharStream input = CharStreams.fromString(query);
        TraceQLLexer lexer = new TraceQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TraceQLParser parser = new TraceQLParser(tokens);
        // ANTLR's default listener prints the error and recovers, which used to hand the visitor a partial tree and
        // let a query with an unsupported construct answer with unfiltered traces (#14093). Collect instead and fail.
        final SyntaxErrors errors = new SyntaxErrors();
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        final ParseTree tree = parser.query();
        if (!errors.messages.isEmpty()) {
            throw new IllegalArgumentException("Invalid TraceQL: " + String.join("; ", errors.messages));
        }
        return tree;
    }

    private static final class SyntaxErrors extends BaseErrorListener {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void syntaxError(final Recognizer<?, ?> recognizer,
                                final Object offendingSymbol,
                                final int line,
                                final int charPositionInLine,
                                final String msg,
                                final RecognitionException e) {
            // OR is the one unsupported construct users meet by accident: Grafana's query builder emits
            // `{(a || b)}` for a multi-select value. Name it instead of echoing the grammar's expectation.
            final String reason = offendingSymbol instanceof Token && "||".equals(((Token) offendingSymbol).getText())
                ? "OR (||) is not supported, select one value at a time" : msg;
            messages.add("line " + line + ":" + charPositionInLine + " " + reason);
        }
    }

    /**
     * Extract query parameters from TraceQL query.
     *
     * @param query TraceQL query string
     * @return TraceQL query parameters
     */
    public static TraceQLParseResult extractParams(String query) {
        try {
            ParseTree tree = parse(query);
            TraceQLQueryVisitor visitor = new TraceQLQueryVisitor();
            return visitor.visit(tree);
        } catch (Throwable t) {
            return TraceQLParseResult.error(t.getMessage());
        }
    }
}
