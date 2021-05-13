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

package org.apache.skywalking.apm.plugin.jdbc.postgresql;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.jdbc.trace.SWPreparedStatement;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link JDBCPrepareStatementWithStringArrayInterceptor} return {@link SWPreparedStatement} instance that wrapper the
 * real preparedStatement instance when the client call <code>org.postgresql.jdbc.PgConnection#prepareStatement(String,
 * String[]) </code> method. method.
 */
public class JDBCPrepareStatementWithStringArrayInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        /**
         * To prevent the <code>org.postgresql.jdbc.prepareStatement(String sql)</code> method from repeating
         * interceptor, Because PGConnection call <code>org.postgresql.jdbc.prepareStatement(String sql)</code> method when
         * the second argument is empty.
         *
         * @see org.postgresql.jdbc.PgConnection#prepareStatement(String, String[])
         **/
        String[] columnNames = (String[]) allArguments[1];
        if (columnNames != null && columnNames.length == 0) {
            return ret;
        }
        return new SWPreparedStatement((Connection) objInst, (PreparedStatement) ret, (ConnectionInfo) objInst.getSkyWalkingDynamicField(), (String) allArguments[0]);
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
