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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;

@RequiredArgsConstructor
public class DatabaseSlowStatementBuilder {
    private final NamingControl namingControl;

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
    private NodeType type = NodeType.Database;
    @Getter
    @Setter
    private String statement;
    @Getter
    @Setter
    private long latency;
    @Getter
    @Setter
    private long timeBucket;

    void prepare() {
        this.serviceName = namingControl.formatServiceName(serviceName);
    }

    DatabaseSlowStatement toDatabaseSlowStatement() {
        DatabaseSlowStatement dbSlowStat = new DatabaseSlowStatement();
        dbSlowStat.setId(id);
        dbSlowStat.setTraceId(traceId);
        dbSlowStat.setDatabaseServiceId(IDManager.ServiceID.buildId(serviceName, type));
        dbSlowStat.setStatement(statement);
        dbSlowStat.setLatency(latency);
        dbSlowStat.setTimeBucket(timeBucket);
        return dbSlowStat;
    }

}
