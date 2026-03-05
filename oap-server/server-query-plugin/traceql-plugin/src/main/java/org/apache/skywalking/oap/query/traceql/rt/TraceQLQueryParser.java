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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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
        return parser.query();
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
            return TraceQLParseResult.error("Failed to parse TraceQL: " + t.getMessage());
        }
    }
}
