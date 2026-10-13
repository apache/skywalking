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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public class DatabaseSlowStatementBuilder implements LALOutputBuilder, TALOutputBuilder {
    public static final String NAME = "SlowSQL";

    private static NamingControl NAMING_CONTROL;
    private static boolean INITIALIZED;

    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private String traceId;
    @Getter
    @Setter
    private String serviceName;
    @Getter
    @Setter
    private Layer layer = Layer.VIRTUAL_DATABASE;
    @Getter
    @Setter
    private String statement;
    @Getter
    @Setter
    private long latency;
    @Getter
    @Setter
    private long timeBucket;
    @Getter
    @Setter
    private long timestamp;

    public DatabaseSlowStatementBuilder() {
    }

    /**
     * Constructor for v1 (Groovy) path where NamingControl is resolved eagerly.
     */
    public DatabaseSlowStatementBuilder(final NamingControl namingControl) {
        NAMING_CONTROL = namingControl;
        INITIALIZED = true;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final LogMetadata metadata, final Object input,
                     final ModuleManager moduleManager) {
        ensureNamingControl(moduleManager);
        // Only populate fields not already set by the LAL extractor.
        if (this.serviceName == null) {
            this.serviceName = metadata.getService();
        }
        if (this.traceId == null) {
            final LogMetadata.TraceContext tc = metadata.getTraceContext();
            if (tc != null) {
                this.traceId = tc.getTraceId();
            }
        }
        if (this.timestamp == 0) {
            this.timestamp = metadata.getTimestamp();
        }
        if (this.timeBucket == 0) {
            this.timeBucket = TimeBucket.getTimeBucket(
                this.timestamp > 0 ? this.timestamp : metadata.getTimestamp(),
                DownSampling.Second);
        }
    }

    /**
     * The span-side lifecycle. The standard fields come from {@code meta}, which carries whatever the rule's
     * extractor overwrote, so a TAL rule names the database with {@code service peer} and the layer with
     * {@code layer "OTLP_VIRTUAL_DATABASE"} exactly as a LAL slow-SQL rule names its own.
     *
     * @param meta          the span's metadata view
     * @param input         the wire span, unread by this builder
     * @param moduleManager for resolving {@code NamingControl} on the first record
     */
    @Override
    public void init(final SpanMetadata meta, final Object input, final ModuleManager moduleManager) {
        ensureNamingControl(moduleManager);
        if (this.serviceName == null) {
            this.serviceName = meta.getService();
        }
        if (StringUtil.isNotEmpty(meta.getLayer())) {
            // A span's layer is a string until here; an unknown name leaves the default rather than failing the
            // record, since the id it builds only depends on the layer's normal flag. Layer.nameOf would answer
            // UNDEFINED for it instead, silently.
            try {
                this.layer = Layer.valueOf(meta.getLayer());
            } catch (final IllegalArgumentException e) {
                log.warn("SlowSQL record names an unknown layer '{}', keeping {}", meta.getLayer(), this.layer);
            }
        }
        if (this.traceId == null) {
            this.traceId = meta.getTraceId();
        }
        if (this.timestamp == 0) {
            this.timestamp = meta.getTimestamp();
        }
        if (this.timeBucket == 0) {
            this.timeBucket = TimeBucket.getTimeBucket(this.timestamp, DownSampling.Second);
        }
    }

    @Override
    public void complete(final SourceReceiver sourceReceiver) {
        if (id == null || latency < 1 || statement == null) {
            if (log.isDebugEnabled()) {
                log.debug("SlowSQL builder incomplete, skipping dispatch: id={}, latency={}, statement={}",
                    id, latency, statement);
            }
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("SlowSQL builder dispatching: service={}, id={}, statement={}, latency={}",
                serviceName, id, statement, latency);
        }
        prepare();
        sourceReceiver.receive(toDatabaseSlowStatement());
    }

    private static void ensureNamingControl(final ModuleManager moduleManager) {
        if (!INITIALIZED) {
            NAMING_CONTROL = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
            INITIALIZED = true;
        }
    }

    public void prepare() {
        this.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
    }

    @Override
    public String outputToJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("type", getClass().getSimpleName());
        obj.addProperty("name", name());
        obj.addProperty("id", id);
        obj.addProperty("traceId", traceId);
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("layer", layer == null ? null : layer.name());
        obj.addProperty("statement", statement);
        obj.addProperty("latency", latency);
        obj.addProperty("timestamp", timestamp);
        obj.addProperty("timeBucket", timeBucket);
        return obj.toString();
    }

    public DatabaseSlowStatement toDatabaseSlowStatement() {
        DatabaseSlowStatement dbSlowStat = new DatabaseSlowStatement();
        dbSlowStat.setId(id);
        dbSlowStat.setTraceId(traceId);
        dbSlowStat.setDatabaseServiceId(IDManager.ServiceID.buildId(serviceName, layer.isNormal()));
        // The agent path cuts to maxSlowSQLLength, whose default is this length, in VirtualDatabaseProcessor.
        dbSlowStat.setStatement(StringUtil.cut(statement, TopNDatabaseStatement.STATEMENT_MAX_LENGTH));
        dbSlowStat.setLatency(latency);
        dbSlowStat.setTimeBucket(timeBucket);
        dbSlowStat.setTimestamp(timestamp);
        return dbSlowStat;
    }
}
