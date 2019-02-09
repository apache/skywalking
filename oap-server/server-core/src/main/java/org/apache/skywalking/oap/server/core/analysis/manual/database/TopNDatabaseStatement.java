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

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.analysis.topn.annotation.TopNType;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.*;

/**
 * Database TopN statement, including Database SQL statement, mongoDB and Redis commands.
 *
 * @author wusheng
 */
@TopNType
@StorageEntity(name = TopNDatabaseStatement.INDEX_NAME, builder = TopNDatabaseStatement.Builder.class, source = Scope.DatabaseSlowStatement)
public class TopNDatabaseStatement extends TopN {
    public static final String INDEX_NAME = "TOP_N_DATABASE_STATEMENT";
    public static final String DATABASE_SERVICE_ID = "db_service_id";

    @Getter @Setter @Column(columnName = DATABASE_SERVICE_ID) private int databaseServiceId;

    @Override public String id() {
        throw new UnexpectedException("id() should not be called.");
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TopNDatabaseStatement statement = (TopNDatabaseStatement)o;
        return databaseServiceId == statement.databaseServiceId;
    }

    @Override public int hashCode() {
        return Objects.hash(databaseServiceId);
    }

    public static class Builder implements StorageBuilder<TopNDatabaseStatement> {

        @Override public TopNDatabaseStatement map2Data(Map<String, Object> dbMap) {
            TopNDatabaseStatement statement = new TopNDatabaseStatement();
            statement.setStatement((String)dbMap.get(STATEMENT));
            statement.setTraceId((String)dbMap.get(TRACE_ID));
            statement.setDuration(((Number)dbMap.get(DURATION)).longValue());
            statement.setDatabaseServiceId(((Number)dbMap.get(DATABASE_SERVICE_ID)).intValue());
            return statement;
        }

        @Override public Map<String, Object> data2Map(TopNDatabaseStatement storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(STATEMENT, storageData.getStatement());
            map.put(TRACE_ID, storageData.getTraceId());
            map.put(DURATION, storageData.getDuration());
            map.put(DATABASE_SERVICE_ID, storageData.getDatabaseServiceId());
            return map;
        }
    }
}
