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
import java.util.Map;

/**
 * Intercept method of {@link org.quartz.core.JobRunShell#run()}.
 * record the quartz job local span.
 */
public class JobRunShellMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final AbstractTag JOB_GROUP = Tags.ofKey("jobGroup");
    private static final AbstractTag JOB_NAME = Tags.ofKey("jobName");
    private static final AbstractTag JOB_DATA_MAP = Tags.ofKey("jobDataMap");

    private static final String EMPTY_JOB_DATA_MAP_STRING = Collections.emptyMap().toString();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        JobDetail jobDetail = (JobDetail) objInst.getSkyWalkingDynamicField();

        String jobGroup = jobDetail.getKey().getGroup();
        String jobName = jobDetail.getKey().getName();
        String operationName = ComponentsDefine.QUARTZ_SCHEDULER.getName() + "/" + jobDetail.getJobClass().getName();

        AbstractSpan span = ContextManager.createLocalSpan(operationName);
        span.setComponent(ComponentsDefine.QUARTZ_SCHEDULER);
        Tags.LOGIC_ENDPOINT.set(span, Tags.VAL_LOCAL_SPAN_AS_LOGIC_ENDPOINT);
        span.tag(JOB_GROUP, jobGroup == null ? "" : jobGroup);
        span.tag(JOB_NAME, jobName == null ? "" : jobName);
        span.tag(JOB_DATA_MAP, getJobDataMapString(jobDetail));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private static String getJobDataMapString(JobDetail jobDetail) {
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        if (jobDataMap != null) {
            Map wrappedMap = jobDataMap.getWrappedMap();
            if (wrappedMap != null) {
                return wrappedMap.toString();
            }
        }
        return EMPTY_JOB_DATA_MAP_STRING;
    }
}
