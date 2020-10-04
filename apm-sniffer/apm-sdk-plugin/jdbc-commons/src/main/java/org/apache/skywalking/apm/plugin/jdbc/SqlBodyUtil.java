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

package org.apache.skywalking.apm.plugin.jdbc;

/**
 * Sql body utility
 */
public class SqlBodyUtil {
    private static final String EMPTY_STRING = "";

    /**
     * Limit sql body size to specify {@code JDBCPluginConfig.Plugin.JDBC.SQL_BODY_MAX_LENGTH}
     * @param sql Sql to limit
     */
    public static String limitSqlBodySize(String sql) {
        if (sql == null) {
            return EMPTY_STRING;
        }
        if (JDBCPluginConfig.Plugin.JDBC.SQL_BODY_MAX_LENGTH > 0 && sql.length() > JDBCPluginConfig.Plugin.JDBC.SQL_BODY_MAX_LENGTH) {
            return sql.substring(0, JDBCPluginConfig.Plugin.JDBC.SQL_BODY_MAX_LENGTH) + "...";
        }
        return sql;
    }
}
