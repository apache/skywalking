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

public class PreparedStatementParameterBuilder {
    private static final String EMPTY_LIST = "[]";
    private Object[] parameters;
    private Integer maxIndex;

    public PreparedStatementParameterBuilder setParameters(Object[] parameters) {
        this.parameters = parameters;
        return this;
    }

    public PreparedStatementParameterBuilder setMaxIndex(int maxIndex) {
        this.maxIndex = maxIndex;
        return this;
    }

    public String build() {
        if (parameters == null) {
            return EMPTY_LIST;
        }

        return getParameterString();
    }

    private String getParameterString() {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < getMaxIndex(); i++) {
            Object parameter = parameters[i];
            if (!first) {
                stringBuilder.append(",");
            }
            stringBuilder.append(parameter);
            first = false;

            //  cut the string as soon as it reached the length limitation
            if (JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH > 0 && (stringBuilder.length() + EMPTY_LIST.length()) > JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH) {
                return format(stringBuilder).substring(0, JDBCPluginConfig.Plugin.JDBC.SQL_PARAMETERS_MAX_LENGTH) + "...";
            }
        }
        return format(stringBuilder);
    }

    private int getMaxIndex() {
        int maxIdx = maxIndex != null ? maxIndex : parameters.length;
        return Math.min(maxIdx, parameters.length);
    }

    private String format(StringBuilder stringBuilder) {
        return String.format("[%s]", stringBuilder.toString());
    }

}
