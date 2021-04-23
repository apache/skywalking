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

package org.apache.skywalking.apm.toolkit.activation.log.log4j.v2.x.async;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.toolkit.logging.common.log.SkyWalkingContext;

/**
 * Log4jLogEvent implements LogEvent, which is a message in the Disruptor Array of the AsyncLoggerConfigDisruptor.class,
 * appended by AsyncLoggerConfig.class. The Log4jLogEvent is enhanced to carry the tid in the synchronization thread
 * using `dynamicField`.
 */

public class Log4jLogEventConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        SkyWalkingContext skyWalkingContext = new SkyWalkingContext(ContextManager.getGlobalTraceId(),
                ContextManager.getSegmentId(), ContextManager.getSpanId());
        objInst.setSkyWalkingDynamicField(skyWalkingContext);
    }
}
