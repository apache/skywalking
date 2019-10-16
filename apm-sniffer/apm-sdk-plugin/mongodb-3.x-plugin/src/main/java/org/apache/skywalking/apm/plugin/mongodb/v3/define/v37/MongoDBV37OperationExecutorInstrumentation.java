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


package org.apache.skywalking.apm.plugin.mongodb.v3.define.v37;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v37.MongoDBV37OperationExecutorInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.HierarchyMatch.byHierarchyMatch;

/**
 * {@link com.mongodb.client.internal.OperationExecutor} which is unified entrance of execute mongo command.
 * so we can intercept {@link com.mongodb.client.internal.OperationExecutor#execute(...)} method
 * to known what command will be execute.
 * <p>
 * support: 3.7.x or higher
 *
 * @author scolia
 * @see MongoDBV37OperationExecutorInterceptor
 */
@SuppressWarnings({"JavadocReference", "Duplicates"})
public class MongoDBV37OperationExecutorInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "com.mongodb.client.internal.OperationExecutor";

    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.mongodb.v3.interceptor.v37.MongoDBV37OperationExecutorInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return byHierarchyMatch(new String[]{ENHANCE_CLASS});
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{new InstanceMethodsInterceptPoint() {
            @Override
            public ElementMatcher<MethodDescription> getMethodsMatcher() {
                return named("execute");
            }

            @Override
            public String getMethodsInterceptor() {
                return INTERCEPTOR_CLASS;
            }

            @Override
            public boolean isOverrideArgs() {
                return false;
            }
        }
        };
    }
}
