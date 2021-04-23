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

package org.apache.skywalking.apm.plugin.thrift;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.plugin.thrift.commons.ReflectionUtils;
import org.apache.skywalking.apm.plugin.thrift.wrapper.ServerInProtocolWrapper;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;

/**
 * Hijack the ProtocolFactory for wrapping the Protocol object to propagate trace context(receiver).
 */
public class TServerInterceptor implements InstanceConstructorInterceptor {
    private static final ILog LOGGER = LogManager.getLogger(TServerInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        try {
            final TProtocolFactory inputProtocolFactory = (TProtocolFactory) ReflectionUtils.getValue(
                TServer.class,
                objInst,
                "inputProtocolFactory_"
            );
            ReflectionUtils.setValue(
                TServer.class,
                objInst,
                "inputProtocolFactory_",
                (TProtocolFactory) trans -> new ServerInProtocolWrapper(inputProtocolFactory.getProtocol(trans))
            );
        } catch (Exception e) {
            LOGGER.error("Failed to hijack TServer's TProtocolFactory.", e);
        }
    }
}
