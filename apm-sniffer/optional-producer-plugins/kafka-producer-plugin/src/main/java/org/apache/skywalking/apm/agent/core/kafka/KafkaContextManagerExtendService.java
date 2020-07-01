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

package org.apache.skywalking.apm.agent.core.kafka;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * For compatible with {@link ContextManagerExtendService}
 */
@OverrideImplementor(ContextManagerExtendService.class)
public class KafkaContextManagerExtendService extends ContextManagerExtendService {
    private static final ILog logger = LogManager.getLogger(KafkaContextManagerExtendService.class);

    public AbstractTracerContext createTraceContext(String operationName, boolean forceSampling) {
        AbstractTracerContext context;
        final int suffixIdx = operationName.lastIndexOf(".");
        if (suffixIdx > -1 && Config.Agent.IGNORE_SUFFIX.contains(operationName.substring(suffixIdx))) {
            context = new IgnoredTracerContext();
        } else {
            SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
            if (forceSampling || samplingService.trySampling()) {
                context = new TracingContext(operationName);
            } else {
                context = new IgnoredTracerContext();
            }
        }

        if (context instanceof IgnoredTracerContext) {
            logger.warn("Ignore all tracing.");
        }

        return context;
    }

    @Override
    public void prepare() {
        logger.warn("KafkaContextManagerExtendService ## prepare............");
        statusChanged(GRPCChannelStatus.CONNECTED); // Kafka manages the status by self.
    }

}
