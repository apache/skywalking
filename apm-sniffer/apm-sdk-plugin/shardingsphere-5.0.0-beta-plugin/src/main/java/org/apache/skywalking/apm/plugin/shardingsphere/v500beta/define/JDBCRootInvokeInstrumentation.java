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

package org.apache.skywalking.apm.plugin.shardingsphere.v500beta.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.shardingsphere.v500beta.JDBCRootInvokeInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

/**
 * {@link JDBCRootInvokeInstrumentation} presents that skywalking intercepts
 * <ul>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement#executeQuery}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement#executeUpdate}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement#execute0}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement#executeQuery}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement#executeUpdate}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement#execute}</li>
 *     <li>{@link org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement#executeBatch}</li>
 * </ul>
 */
public class JDBCRootInvokeInstrumentation extends AbstractShardingSphereV500BetaInstrumentation {
    
    private static final String[] ENHANCE_CLASSES = {
            "org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement",
            "org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement"
    };
    
    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("executeQuery")
                                .or(named("executeUpdate"))
                                .or(named("execute0"))
                                .or(named("execute").and(takesNoArguments()))
                                .or(named("executeBatch"));
                    }
                    
                    @Override
                    public String getMethodsInterceptor() {
                        return JDBCRootInvokeInterceptor.class.getName();
                    }
                    
                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byMultiClassMatch(ENHANCE_CLASSES);
    }
}
