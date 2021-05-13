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

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.plugin.jdbc.define.Constants;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class JDBCPreparedStatementNullSetterInstanceMethodsInterceptPoint implements InstanceMethodsInterceptPoint {
    @Override
    public ElementMatcher<MethodDescription> getMethodsMatcher() {
        return named("setNull");
    }

    @Override
    public String getMethodsInterceptor() {
        return Constants.PREPARED_STATEMENT_NULL_SETTER_METHODS_INTERCEPTOR;
    }

    @Override
    public boolean isOverrideArgs() {
        return false;
    }
}
