/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.jdbc.mysql.define;

import org.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link StatementEnhanceInfos} contain the {@link org.skywalking.apm.plugin.jdbc.trace.ConnectionInfo} and
 * <code>sql</code> for trace mysql.
 *
 * @author zhangxin
 */
public class StatementEnhanceInfos {
    private ConnectionInfo connectionInfo;
    private String statementName;
    private String sql;

    public StatementEnhanceInfos(ConnectionInfo connectionInfo, String sql, String statementName) {
        this.connectionInfo = connectionInfo;
        this.sql = sql;
        this.statementName = statementName;
    }

    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public String getSql() {
        return sql;
    }

    public String getStatementName() {
        return statementName;
    }
}
