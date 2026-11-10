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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * Every log line of {@code org.apache.skywalking}, at {@code TRACE}, into the event log of the side that is analyzing.
 * A listener's class logger and the logger of the script that ports it are one group, and so are the parseSegment
 * lines of {@code TraceAnalyzer} and {@code SegmentAnalysisListener} and the native runner's; any other logger is its
 * own group, the same Java on both sides.
 */
final class CheckerLogCapture extends AbstractAppender {
    private static final String ROOT = "org.apache.skywalking";
    private static final String LISTENER = "org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.";
    private static final String SCRIPT = "org.apache.skywalking.trace.analysis.script.";
    private static final Map<String, String> GROUPS = groups();

    private volatile CheckerEventLog current;
    /**
     * The configurations of {@code org.apache.skywalking} and the loggers under it the capture removed, so their levels
     * and appenders do not hold back a line, restored by {@link #uninstall()}.
     */
    private final List<LoggerConfig> replaced = new ArrayList<>();

    private CheckerLogCapture() {
        super("native-trace-checker", null, null, true, Property.EMPTY_ARRAY);
    }

    /**
     * @return the capture, replacing the configurations of {@code org.apache.skywalking} and every logger under it with
     * one at TRACE that passes those lines to the capture only, not to the console
     */
    static CheckerLogCapture install() {
        final CheckerLogCapture capture = new CheckerLogCapture();
        capture.start();
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        for (final LoggerConfig existing : new ArrayList<>(configuration.getLoggers().values())) {
            if (existing.getName().equals(ROOT) || existing.getName().startsWith(ROOT + ".")) {
                capture.replaced.add(existing);
                configuration.removeLogger(existing.getName());
            }
        }
        final LoggerConfig config = new LoggerConfig(ROOT, Level.TRACE, false);
        config.addAppender(capture, Level.TRACE, null);
        configuration.addLogger(ROOT, config);
        context.updateLoggers();
        return capture;
    }

    /**
     * Put back the configuration {@link #install()} replaced.
     */
    void uninstall() {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        configuration.removeLogger(ROOT);
        replaced.forEach(config -> configuration.addLogger(config.getName(), config));
        context.updateLoggers();
        stop();
    }

    /**
     * Run the captured loggers at the level, TRACE being every line.
     */
    void setLevel(final Level level) {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getLoggerConfig(ROOT).setLevel(level);
        context.updateLoggers();
    }

    void recordInto(final CheckerEventLog log) {
        this.current = log;
    }

    @Override
    public void append(final LogEvent event) {
        final CheckerEventLog log = current;
        if (log == null || !log.isRecording()) {
            return;
        }
        final Message message = event.getMessage();
        final String pattern = message instanceof ParameterizedMessage
            ? ((ParameterizedMessage) message).getFormat() : message.getFormat();
        log.event("log " + group(event.getLoggerName()) + " " + event.getLevel() + " " + pattern + " ["
            + arguments(message.getParameters()) + "]"
            + (event.getThrown() == null ? "" : " thrown " + event.getThrown().getClass().getName()));
    }

    /**
     * A log line's arguments as SLF4J writes them, each quoted, a null left bare, so null and the text "null" differ.
     */
    static String arguments(final Object[] params) {
        if (params == null) {
            return "";
        }
        return Arrays.stream(params)
                     .map(p -> p == null ? "null" : CheckerObjectRenderer.quote(String.valueOf(p)))
                     .collect(Collectors.joining(", "));
    }

    private static String group(final String loggerName) {
        return GROUPS.getOrDefault(loggerName, loggerName);
    }

    private static Map<String, String> groups() {
        final Map<String, String> groups = new HashMap<>();
        groups.put(LISTENER + "RPCAnalysisListener", "rpc");
        groups.put(SCRIPT + "native-rpc", "rpc");
        groups.put(LISTENER + "EndpointDepFromCrossThreadAnalysisListener", "cross-thread");
        groups.put(SCRIPT + "native-endpoint-cross-thread", "cross-thread");
        groups.put(LISTENER + "NetworkAddressAliasMappingListener", "alias");
        groups.put(SCRIPT + "native-network-alias", "alias");
        groups.put(LISTENER + "SegmentAnalysisListener", "segment");
        groups.put("org.apache.skywalking.oap.server.analyzer.provider.trace.parser.TraceAnalyzer", "segment");
        groups.put("org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeRuleRunner", "segment");
        groups.put(SCRIPT + "native-segment", "segment");
        groups.put(LISTENER + "VirtualServiceAnalysisListener", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualCacheProcessor", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualDatabaseProcessor", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualMQProcessor", "virtual-service");
        groups.put(SCRIPT + "native-virtual-service", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualGenAIProcessor", "genai");
        groups.put("org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer", "genai");
        groups.put(SCRIPT + "native-virtual-genai", "genai");
        groups.put(LISTENER + "GenAIEvaluationAnalysisListener", "evaluation");
        groups.put(SCRIPT + "native-ai-evaluation", "evaluation");
        return groups;
    }
}
