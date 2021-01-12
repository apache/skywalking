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

package org.apache.skywalking.apm.plugin.jdbc.define;

import java.util.Arrays;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link StatementEnhanceInfos} contain the {@link ConnectionInfo} and
 * <code>sql</code> for trace mysql.
 */
public class StatementEnhanceInfos {
    private ConnectionInfo connectionInfo;
    private String statementName;
    private String sql;
    private Object[] parameters;
    private int maxIndex = 0;

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

    public void setParameter(int index, final Object parameter) {
        maxIndex = maxIndex > index ? maxIndex : index;
        index--; // start from 1
        if (parameters == null) {
            final int initialSize = Math.max(16, maxIndex);
            parameters = new Object[initialSize];
            Arrays.fill(parameters, null);
        }
        int length = parameters.length;
        if (index >= length) {
            int newSize = Math.max(index + 1, length * 2);
            Object[] newParameters = new Object[newSize];
            System.arraycopy(parameters, 0, newParameters, 0, length);
            Arrays.fill(newParameters, length, newSize, null);
            parameters = newParameters;
        }
        parameters[index] = parameter;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public int getMaxIndex() {
        return maxIndex;
    }
}
