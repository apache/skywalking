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

public class SqlBodyBuilder {
    private static final String EMPTY_STRING = "";
    private int maxLength = 0;
    private String sql;

    public SqlBodyBuilder setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public SqlBodyBuilder setSqlBody(String sqlBody) {
        this.sql = sqlBody;
        return this;
    }

    public String build() {
        if (sql == null) {
            return EMPTY_STRING;
        }
        if (maxLength > 0 && sql.length() > maxLength) {
            return sql.substring(0, maxLength) + "...";
        }
        return sql;
    }
}
