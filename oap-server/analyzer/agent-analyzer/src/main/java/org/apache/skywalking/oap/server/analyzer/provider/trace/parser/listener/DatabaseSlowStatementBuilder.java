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

import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class DatabaseSlowStatementBuilder implements LALOutputBuilder {
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
     * Constructor for v1 (Groovy) path which doesn't use {@link #init}.
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
    public void init(final LogData logData, final Optional<Object> extraLog,
                     final ModuleManager moduleManager) {
        if (!INITIALIZED) {
            NAMING_CONTROL = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
            INITIALIZED = true;
        }
        // Only populate fields not already set by the LAL extractor.
        if (this.serviceName == null) {
            this.serviceName = logData.getService();
        }
        if (this.traceId == null) {
            this.traceId = logData.getTraceContext().getTraceId();
        }
        if (this.timestamp == 0) {
            this.timestamp = logData.getTimestamp();
        }
        if (this.timeBucket == 0) {
            this.timeBucket = TimeBucket.getTimeBucket(
                this.timestamp > 0 ? this.timestamp : logData.getTimestamp(),
                DownSampling.Second);
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

    public void prepare() {
        this.serviceName = NAMING_CONTROL.formatServiceName(serviceName);
    }

    public DatabaseSlowStatement toDatabaseSlowStatement() {
        DatabaseSlowStatement dbSlowStat = new DatabaseSlowStatement();
        dbSlowStat.setId(id);
        dbSlowStat.setTraceId(traceId);
        dbSlowStat.setDatabaseServiceId(IDManager.ServiceID.buildId(serviceName, layer.isNormal()));
        dbSlowStat.setStatement(statement);
        dbSlowStat.setLatency(latency);
        dbSlowStat.setTimeBucket(timeBucket);
        dbSlowStat.setTimestamp(timestamp);
        return dbSlowStat;
    }
}
