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

package org.apache.skywalking.oap.log.analyzer.dsl;

import com.google.protobuf.Message;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import lombok.Getter;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;

import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.source.Log;

/**
 * The binding bridge between OAP and the DSL, which provides some convenient methods to ease the use of the raw {@link groovy.lang.Binding#setProperty(java.lang.String, java.lang.Object)} and {@link
 * groovy.lang.Binding#getProperty(java.lang.String)}.
 */
public class Binding extends groovy.lang.Binding {
    public static final String KEY_LOG = "log";

    public static final String KEY_PARSED = "parsed";

    public static final String KEY_SAVE = "save";

    public static final String KEY_ABORT = "abort";

    public static final String KEY_METRICS_CONTAINER = "metrics_container";

    public static final String KEY_LOG_CONTAINER = "log_container";

    public static final String KEY_DATABASE_SLOW_STATEMENT = "database_slow_statement";

    public static final String KEY_SAMPLED_TRACE = "sampled_trace";

    public Binding() {
        setProperty(KEY_PARSED, new Parsed());
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

    public Binding databaseSlowStatement(DatabaseSlowStatementBuilder databaseSlowStatementBuilder) {
        setProperty(KEY_DATABASE_SLOW_STATEMENT, databaseSlowStatementBuilder);
        return this;
    }

    public SampledTraceBuilder sampledTraceBuilder() {
        return (SampledTraceBuilder) getProperty(KEY_SAMPLED_TRACE);
    }

    public Binding sampledTrace(SampledTraceBuilder sampledTraceBuilder) {
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

    /**
     * Set the metrics container to store all metrics generated from the pipeline,
     * if no container is set, all generated metrics will be sent to MAL engine for further processing,
     * if metrics container is set, all metrics are only stored in the container, and won't be sent to MAL.
     *
     * @param container the metrics container
     */
    public Binding metricsContainer(List<SampleFamily> container) {
        setProperty(KEY_METRICS_CONTAINER, container);
        return this;
    }

    public Optional<List<SampleFamily>> metricsContainer() {
        // noinspection unchecked
        return Optional.ofNullable((List<SampleFamily>) getProperty(KEY_METRICS_CONTAINER));
    }

    /**
     * Set the log container to store the final log if it should be persisted in storage,
     * if no container is set, the final log will be sent to source receiver,
     * if log container is set, the log is only stored in the container, and won't be sent to source receiver.
     *
     * @param container the log container
     */
    public Binding logContainer(AtomicReference<Log> container) {
        setProperty(KEY_LOG_CONTAINER, container);
        return this;
    }

    public Optional<AtomicReference<Log>> logContainer() {
        // noinspection unchecked
        return Optional.ofNullable((AtomicReference<Log>) getProperty(KEY_LOG_CONTAINER));
    }

    public static class Parsed extends GroovyObjectSupport {
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

        @SuppressWarnings("unused")
        public Object propertyMissing(final String name) {
            return getAt(name);
        }

        static Object getField(Object obj, String name) {
            try {
                Closure<?> c = new Closure<Object>(obj, obj) {
                };
                return c.getProperty(name);
            } catch (MissingPropertyException ignored) {
            }
            return null;
        }
    }
}
