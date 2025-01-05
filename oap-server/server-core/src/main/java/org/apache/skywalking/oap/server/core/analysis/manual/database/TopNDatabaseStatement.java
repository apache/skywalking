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

package org.apache.skywalking.oap.server.core.analysis.manual.database;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

/**
 * Database TopN statement, including Database SQL statement, mongoDB and Redis commands.
 */
@Stream(name = TopNDatabaseStatement.INDEX_NAME, scopeId = DefaultScopeDefine.DATABASE_SLOW_STATEMENT, builder = TopNDatabaseStatement.Builder.class, processor = TopNStreamProcessor.class)
@BanyanDB.TimestampColumn(TopN.TIMESTAMP)
public class TopNDatabaseStatement extends TopN {
    public static final String INDEX_NAME = "top_n_database_statement";

    @Setter
    private String id;
    @Getter
    @Setter
    @Column(name = STATEMENT, length = 2000, storageOnly = true)
    private String statement;

    @Override
    public StorageID id() {
        return new StorageID().appendMutant(null, id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TopNDatabaseStatement statement = (TopNDatabaseStatement) o;
        return Objects.equals(getEntityId(), statement.getEntityId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntityId());
    }

    public static class Builder implements StorageBuilder<TopNDatabaseStatement> {
        @Override
        public TopNDatabaseStatement storage2Entity(final Convert2Entity converter) {
            TopNDatabaseStatement statement = new TopNDatabaseStatement();
            statement.setStatement((String) converter.get(STATEMENT));
            statement.setTraceId((String) converter.get(TRACE_ID));
            statement.setLatency(((Number) converter.get(LATENCY)).longValue());
            statement.setEntityId((String) converter.get(ENTITY_ID));
            statement.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            statement.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            return statement;
        }

        @Override
        public void entity2Storage(final TopNDatabaseStatement storageData, final Convert2Storage converter) {
            converter.accept(STATEMENT, storageData.getStatement());
            converter.accept(TRACE_ID, storageData.getTraceId());
            converter.accept(LATENCY, storageData.getLatency());
            converter.accept(ENTITY_ID, storageData.getEntityId());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(TIMESTAMP, storageData.getTimestamp());
        }
    }
}
