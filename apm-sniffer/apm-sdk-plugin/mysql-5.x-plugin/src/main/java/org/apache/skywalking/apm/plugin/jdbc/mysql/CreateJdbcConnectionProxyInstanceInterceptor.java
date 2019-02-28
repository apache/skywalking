/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import com.mysql.cj.api.jdbc.JdbcConnection;
import com.mysql.cj.core.conf.url.ConnectionUrl;
import com.mysql.cj.core.conf.url.HostInfo;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.jdbc.mysql.wrapper.JdbcConnectionWrapper;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

public class CreateJdbcConnectionProxyInstanceInterceptor implements StaticMethodsAroundInterceptor {
    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {
    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        ConnectionUrl connectionUrl = (ConnectionUrl)allArguments[0];

        StringBuilder hosts = new StringBuilder();
        for (HostInfo info : connectionUrl.getHostsList()) {
            hosts.append(info.getHost()).append(":").append(info.getPort()).append(",");
        }
        ConnectionInfo connectionInfo = new ConnectionInfo(ComponentsDefine.MYSQL_JDBC_DRIVER, "Mysql", hosts.toString(), connectionUrl.getDatabase());
        return new JdbcConnectionWrapper((JdbcConnection)ret, connectionInfo);
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {

    }
}
