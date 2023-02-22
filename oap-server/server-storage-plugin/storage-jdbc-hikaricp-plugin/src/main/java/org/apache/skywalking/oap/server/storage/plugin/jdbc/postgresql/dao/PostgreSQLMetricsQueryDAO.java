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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetricsQueryDAO;

public class PostgreSQLMetricsQueryDAO extends JDBCMetricsQueryDAO {

    public PostgreSQLMetricsQueryDAO(JDBCClient h2Client) {
        super(h2Client);
    }

    @Override
    protected StringBuilder buildMetricsValueSql(String op, String valueColumnName, String conditionName) {
        return new StringBuilder(
                "select " + Metrics.ENTITY_ID + " id, " + op + "(" + valueColumnName + ") as result from " + conditionName + " where ");
    }
}
