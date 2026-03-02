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
 */

package org.apache.skywalking.oap.log.analyzer.v2.dsl;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import lombok.Getter;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.source.Log;

/**
 * Mutable property storage for a single LAL script execution cycle.
 *
 * <p>A new Binding is created for each incoming log in
 * {@link org.apache.skywalking.oap.log.analyzer.provider.log.listener.LogFilterListener#parse}.
 * It carries all per-log state through the compiled LAL pipeline:
 * <ul>
 *   <li>{@code log} — the incoming {@code LogData.Builder}</li>
 *   <li>{@code parsed} — structured data extracted by json/text/yaml parsers</li>
 *   <li>{@code save}/{@code abort} — control flags set by extractor/sink logic</li>
 *   <li>{@code metrics_container} — optional list for LAL-extracted metrics (log-MAL)</li>
 *   <li>{@code log_container} — optional container for the built {@code Log} source object</li>
 * </ul>
 *
 * <p>The Binding is injected into {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec}
 * via a ThreadLocal ({@code BINDING}), so all Spec methods ({@code FilterSpec}, {@code ExtractorSpec},
 * {@code SinkSpec}) can access the current log data without explicit parameter passing.
 */
public class Binding {
    public static final String KEY_LOG = "log";
    public static final String KEY_PARSED = "parsed";
    public static final String KEY_SAVE = "save";
    public static final String KEY_ABORT = "abort";
    public static final String KEY_METRICS_CONTAINER = "metrics_container";
    public static final String KEY_LOG_CONTAINER = "log_container";
    public static final String KEY_DATABASE_SLOW_STATEMENT = "database_slow_statement";
    public static final String KEY_SAMPLED_TRACE = "sampled_trace";

    private final Map<String, Object> properties = new HashMap<>();

    public Binding() {
        setProperty(KEY_PARSED, new Parsed());
    }

    public void setProperty(final String name, final Object value) {
        properties.put(name, value);
    }

    public Object getProperty(final String name) {
        return properties.get(name);
    }

    public Binding log(final LogData.Builder log) {
        setProperty(KEY_LOG, log);
        setProperty(KEY_SAVE, true);
        setProperty(KEY_ABORT, false);
        setProperty(KEY_METRICS_CONTAINER, null);
        setProperty(KEY_LOG_CONTAINER, null);
        parsed().log = log;
        return this;
    }

    public Binding log(final LogData log) {
        return log(log.toBuilder());
    }

    public LogData.Builder log() {
        return (LogData.Builder) getProperty(KEY_LOG);
    }

    public Binding extraLog(final Message extraLog) {
        parsed().extraLog = extraLog;
        return this;
    }

    public Message extraLog() {
        return parsed().getExtraLog();
    }

    public Binding parsed(final Matcher parsed) {
        parsed().matcher = parsed;
        return this;
    }

    public Binding parsed(final Map<String, Object> parsed) {
        parsed().map = parsed;
        return this;
    }

    public Parsed parsed() {
        return (Parsed) getProperty(KEY_PARSED);
    }

    public DatabaseSlowStatementBuilder databaseSlowStatement() {
        return (DatabaseSlowStatementBuilder) getProperty(KEY_DATABASE_SLOW_STATEMENT);
    }

    public Binding databaseSlowStatement(final DatabaseSlowStatementBuilder databaseSlowStatementBuilder) {
        setProperty(KEY_DATABASE_SLOW_STATEMENT, databaseSlowStatementBuilder);
        return this;
    }

    public SampledTraceBuilder sampledTraceBuilder() {
        return (SampledTraceBuilder) getProperty(KEY_SAMPLED_TRACE);
    }

    public Binding sampledTrace(final SampledTraceBuilder sampledTraceBuilder) {
        setProperty(KEY_SAMPLED_TRACE, sampledTraceBuilder);
        return this;
    }

    public Binding save() {
        setProperty(KEY_SAVE, true);
        return this;
    }

    public Binding drop() {
        setProperty(KEY_SAVE, false);
        return this;
    }

    public boolean shouldSave() {
        return (boolean) getProperty(KEY_SAVE);
    }

    public Binding abort() {
        setProperty(KEY_ABORT, true);
        return this;
    }

    public boolean shouldAbort() {
        return (boolean) getProperty(KEY_ABORT);
    }

    public Binding metricsContainer(final List<SampleFamily> container) {
        setProperty(KEY_METRICS_CONTAINER, container);
        return this;
    }

    @SuppressWarnings("unchecked")
    public Optional<List<SampleFamily>> metricsContainer() {
        return Optional.ofNullable((List<SampleFamily>) getProperty(KEY_METRICS_CONTAINER));
    }

    public Binding logContainer(final AtomicReference<Log> container) {
        setProperty(KEY_LOG_CONTAINER, container);
        return this;
    }

    @SuppressWarnings("unchecked")
    public Optional<AtomicReference<Log>> logContainer() {
        return Optional.ofNullable((AtomicReference<Log>) getProperty(KEY_LOG_CONTAINER));
    }

    public static class Parsed {
        @Getter
        private Matcher matcher;

        @Getter
        private Map<String, Object> map;

        @Getter
        private Message.Builder log;

        @Getter
        private Message extraLog;

        public Object getAt(final String key) {
            Object result;
            if (matcher != null && (result = matcher.group(key)) != null) {
                return result;
            }
            if (map != null && (result = map.get(key)) != null) {
                return result;
            }
            if (extraLog != null && (result = getField(extraLog, key)) != null) {
                return result;
            }
            if (log != null && (result = getField(log, key)) != null) {
                return result;
            }
            return null;
        }

        public static Object getField(final Object obj, final String name) {
            if (obj instanceof Message) {
                Descriptors.FieldDescriptor fd =
                    ((Message) obj).getDescriptorForType().findFieldByName(name);
                if (fd == null) {
                    fd = ((Message) obj).getDescriptorForType()
                        .findFieldByName(camelToSnake(name));
                }
                if (fd != null) {
                    return ((Message) obj).getField(fd);
                }
            }
            if (obj instanceof Message.Builder) {
                Descriptors.FieldDescriptor fd =
                    ((Message.Builder) obj).getDescriptorForType().findFieldByName(name);
                if (fd == null) {
                    fd = ((Message.Builder) obj).getDescriptorForType()
                        .findFieldByName(camelToSnake(name));
                }
                if (fd != null) {
                    return ((Message.Builder) obj).getField(fd);
                }
            }
            return null;
        }

        private static String camelToSnake(final String name) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                final char c = name.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
