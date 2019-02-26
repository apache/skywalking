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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

import java.lang.reflect.Method;

/**
 * ConnectionImpl#getInstance in mysql-5.x has 5 parameters such as
 *  getInstance(String hostToConnectTo, int portToConnectTo, Properties info, String databaseToConnectTo, String url)
 * @author: dingshaocheng
 */
public class ConnectionCreate5xInterceptor implements StaticMethodsAroundInterceptor {


    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Object ret) {
        if (ret instanceof EnhancedInstance) {
            //i think on multiHost connection, we should focus on the executed connection instead of all connections
            String host = new StringBuilder(allArguments[0].toString())
                    .append(":")
                    .append(allArguments[1].toString())
                    .toString();
            ConnectionInfo connectionInfo = new ConnectionInfo(ComponentsDefine.MYSQL_JDBC_DRIVER, "Mysql", host, allArguments[3].toString());
            ((EnhancedInstance) ret).setSkyWalkingDynamicField(connectionInfo);
        }
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Throwable t) {

    }
}
