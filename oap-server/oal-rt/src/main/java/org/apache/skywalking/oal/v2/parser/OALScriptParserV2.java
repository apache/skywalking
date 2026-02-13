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

package org.apache.skywalking.oal.v2.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import lombok.Getter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.skywalking.oal.rt.grammar.OALLexer;
import org.apache.skywalking.oal.rt.grammar.OALParser;
import org.apache.skywalking.oal.v2.model.MetricDefinition;

/**
 * V2 parser for OAL scripts.
 *
 * This parser uses ANTLR grammar and converts the parse tree to V2 immutable models.
 *
 * Example usage:
 * <pre>
 * String oalScript = "service_resp_time = from(Service.latency).longAvg();";
 * OALScriptParserV2 parser = OALScriptParserV2.parse(oalScript);
 *
 * List&lt;MetricDefinition&gt; metrics = parser.getMetrics();
 * for (MetricDefinition metric : metrics) {
 *     // Process metric.getName()
 *     // Process metric.getSource().getName()
 *     // Process metric.getAggregationFunction().getName()
 * }
 * </pre>
 */
public class OALScriptParserV2 {

    @Getter
    private final List<MetricDefinition> metrics;

    @Getter
    private final List<String> disabledSources;

    private final String fileName;

    private OALScriptParserV2(List<MetricDefinition> metrics, List<String> disabledSources, String fileName) {
        this.metrics = metrics;
        this.disabledSources = disabledSources;
        this.fileName = fileName;
    }

    /**
     * Parse OAL script from string.
     *
     * @param script OAL script content
     * @return parser result
     * @throws IOException if parsing fails
     */
    public static OALScriptParserV2 parse(String script) throws IOException {
        return parse(script, "oal-script");
    }

    /**
     * Parse OAL script from string with filename.
     *
     * @param script OAL script content
     * @param fileName filename for error reporting
     * @return parser result
     * @throws IOException if parsing fails
     */
    public static OALScriptParserV2 parse(String script, String fileName) throws IOException {
        return parse(new StringReader(script), fileName);
    }

    /**
     * Parse OAL script from reader.
     *
     * @param reader reader with OAL script content
     * @param fileName filename for error reporting
     * @return parser result
     * @throws IOException if parsing fails
     * @throws IllegalArgumentException if syntax errors are found
     */
    public static OALScriptParserV2 parse(Reader reader, String fileName) throws IOException {
        // Create lexer
        OALLexer lexer = new OALLexer(CharStreams.fromReader(reader));

        // Create token stream
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Create parser
        OALParser parser = new OALParser(tokens);

        // Add custom error listener to collect detailed error information
        OALErrorListener errorListener = new OALErrorListener(fileName);
        parser.removeErrorListeners(); // Remove default console error listener
        parser.addErrorListener(errorListener);

        // Parse the script
        OALParser.RootContext root = parser.root();

        // Check for syntax errors
        if (errorListener.hasErrors()) {
            throw new IllegalArgumentException(errorListener.getFormattedErrors());
        }

        // Walk the parse tree with V2 listener
        OALListenerV2 listener = new OALListenerV2(fileName);
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, root);

        return new OALScriptParserV2(listener.getMetrics(), listener.getDisabledSources(), fileName);
    }

    /**
     * Get metrics count.
     */
    public int getMetricsCount() {
        return metrics.size();
    }

    /**
     * Check if any metrics were parsed.
     */
    public boolean hasMetrics() {
        return !metrics.isEmpty();
    }

    /**
     * Check if any sources were disabled.
     */
    public boolean hasDisabledSources() {
        return !disabledSources.isEmpty();
    }
}
