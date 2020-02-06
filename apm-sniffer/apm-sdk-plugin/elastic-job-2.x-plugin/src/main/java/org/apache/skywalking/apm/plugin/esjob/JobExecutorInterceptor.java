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


package org.apache.skywalking.apm.plugin.esjob;

import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.executor.ShardingContexts;
import com.google.common.base.Strings;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link JobExecutorInterceptor} enhances {@link com.dangdang.ddframe.job.executor.AbstractElasticJobExecutor#process(ShardingContext)}
 * ,creating a local span that records job execution.
 * 
 * @author gaohongtao
 */
public class JobExecutorInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ShardingContexts shardingContexts = (ShardingContexts)allArguments[0];
        Integer item = (Integer)allArguments[1];
        ShardingContext shardingContext = new ShardingContext(shardingContexts, item);
        String operateName = shardingContext.getJobName();
        if (!Strings.isNullOrEmpty(shardingContext.getShardingParameter())) {
            operateName += "-" + shardingContext.getShardingParameter();
        }
        AbstractSpan span = ContextManager.createLocalSpan(operateName);
        span.setComponent(ComponentsDefine.ELASTIC_JOB);
        span.tag(Tags.ofKey("sharding_context"), shardingContext.toString());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
