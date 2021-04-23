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

package org.apache.skywalking.apm.plugin.thrift.client;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.plugin.thrift.commons.ReflectionUtils;
import org.apache.skywalking.apm.plugin.thrift.wrapper.ClientOutProtocolWrapper;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;

/**
 * Hijack the TProtocolFactory for wrapping the Protocol object to propagate trace context(write out).
 *
 * @see TAsyncClient
 */
public class TAsyncClientInterceptor implements InstanceConstructorInterceptor {
    private static final ILog LOGGER = LogManager.getLogger(TAsyncClientInterceptor.class);

    @Override
    public void onConstruct(final EnhancedInstance objInst, final Object[] objects) {
        try {
            ReflectionUtils.setValue(
                TAsyncClient.class,
                objInst,
                "___protocolFactory",
                (TProtocolFactory) transport ->
                    new ClientOutProtocolWrapper(((TProtocolFactory) objects[0]).getProtocol(transport))
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Failed to hijack TAsyncClient's TProtocolFactory.", e);
        }
    }
}
