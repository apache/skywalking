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

package org.apache.skywalking.apm.plugin.sentinel.v1;

import com.alibaba.csp.sentinel.AsyncEntry;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.apache.skywalking.apm.plugin.sentinel.v1.Constants.SENTINEL_SPAN;

/**
 * {@link SentinelCtEntryConstructorInterceptor} get <code>CommandKey</code> or <code>CollapserKey</code> as the
 * operation name prefix of span when the constructor that the class hierarchy <code>com.netflix.hystrix.HystrixCommand</code>
 * invoked.
 */
public class SentinelCtEntryConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (!(objInst instanceof AsyncEntry)) {
            ResourceWrapper resourceWrapper = (ResourceWrapper) allArguments[0];
            AbstractSpan activeSpan = ContextManager.createLocalSpan("Sentinel/" + resourceWrapper.getName());
            activeSpan.setComponent(ComponentsDefine.SENTINEL);
            objInst.setSkyWalkingDynamicField(activeSpan);

            ContextManager.getRuntimeContext().put(
                    SENTINEL_SPAN,
                    activeSpan
            );
        }
    }
}
