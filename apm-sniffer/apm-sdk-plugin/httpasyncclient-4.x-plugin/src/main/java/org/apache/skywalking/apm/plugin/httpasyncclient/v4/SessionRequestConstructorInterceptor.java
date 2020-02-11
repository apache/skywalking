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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL;

/**
 * hold the snapshot in SkyWalkingDynamicField
 */
public class SessionRequestConstructorInterceptor implements InstanceConstructorInterceptor {
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (ContextManager.isActive()) {
            if (ContextManager.activeSpan().isExit()) {
                CONTEXT_LOCAL.remove();
                return;
            }
            ContextSnapshot snapshot = ContextManager.capture();
            objInst.setSkyWalkingDynamicField(new Object[] {
                snapshot,
                CONTEXT_LOCAL.get()
            });
        }
        CONTEXT_LOCAL.remove();
    }
}
