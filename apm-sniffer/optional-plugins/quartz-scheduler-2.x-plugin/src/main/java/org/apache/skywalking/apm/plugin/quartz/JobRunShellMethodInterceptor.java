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

package org.apache.skywalking.apm.plugin.quartz;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Intercept method of {@link org.quartz.core.JobRunShell#run()}.
 * record the quartz job local span.
 */
public class JobRunShellMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final AbstractTag JOB_GROUP = Tags.ofKey("jobGroup");
    private static final AbstractTag JOB_DATA_MAP = Tags.ofKey("jobDataMap");

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        JobDetail jobDetail = (JobDetail) objInst.getSkyWalkingDynamicField();

        String jobName = jobDetail.getKey().getName();
        String jobGroup = jobDetail.getKey().getGroup();
        String operationName = ComponentsDefine.QUARTZ_SCHEDULER.getName() + "/" + jobName;

        AbstractSpan span = ContextManager.createLocalSpan(operationName);
        span.setComponent(ComponentsDefine.QUARTZ_SCHEDULER);
        Tags.LOGIC_ENDPOINT.set(span, Tags.VAL_LOCAL_SPAN_AS_LOGIC_ENDPOINT);
        span.tag(JOB_GROUP, jobGroup == null ? "" : jobGroup);
        span.tag(JOB_DATA_MAP, getJobDataMap(jobDetail).toString());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

    private static Map<String, String> getJobDataMap(JobDetail jobDetail) {
        JobDataMap originalJobDataMap = jobDetail.getJobDataMap();
        if (originalJobDataMap != null) {
            Map<String, String> jobDataMap = new HashMap();
            for (String key : originalJobDataMap.getKeys()) {
                Object value = originalJobDataMap.get(key);
                jobDataMap.put(key, value != null ? value.toString() : "");
            }
            return jobDataMap;
        }

        return Collections.emptyMap();
    }
}
