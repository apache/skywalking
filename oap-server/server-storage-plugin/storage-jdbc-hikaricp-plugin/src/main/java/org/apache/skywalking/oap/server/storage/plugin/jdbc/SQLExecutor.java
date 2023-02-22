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

package org.apache.skywalking.oap.server.storage.plugin.jdbc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A SQL executor.
 */
@EqualsAndHashCode(of = "sql")
@RequiredArgsConstructor
@Slf4j
public class SQLExecutor implements InsertRequest, UpdateRequest {
    private final String sql;
    private final List<Object> param;
    private final SessionCacheCallback callback;
    @Getter
    private List<SQLExecutor> additionalSQLs;

    public void invoke(Connection connection) throws SQLException {
        final var preparedStatement = connection.prepareStatement(sql);
        setParameters(preparedStatement);
        if (log.isDebugEnabled()) {
            log.debug("execute sql in batch: {}, parameters: {}", sql, param);
        }
        preparedStatement.execute();
        if (additionalSQLs != null) {
            for (SQLExecutor sqlExecutor : additionalSQLs) {
                sqlExecutor.invoke(connection);
            }
        }
    }

    public void setParameters(PreparedStatement preparedStatement) throws SQLException {
        for (int i = 0; i < param.size(); i++) {
            preparedStatement.setObject(i + 1, param.get(i));
        }
    }

    @Override
    public String toString() {
        return sql;
    }

    public void appendAdditionalSQLs(List<SQLExecutor> sqlExecutors) {
        if (additionalSQLs == null) {
            additionalSQLs = new ArrayList<>();
        }
        additionalSQLs.addAll(sqlExecutors);
    }

    @Override
    public void onInsertCompleted() {
        if (callback != null)
            callback.onInsertCompleted();
    }

    @Override
    public void onUpdateFailure() {
        if (callback != null)
            callback.onUpdateFailure();
    }
}
