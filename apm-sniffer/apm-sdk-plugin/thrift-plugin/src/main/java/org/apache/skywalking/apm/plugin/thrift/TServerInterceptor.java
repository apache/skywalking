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

import java.lang.reflect.Field;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.plugin.thrift.wrapper.ServerInProtocolWrapper;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;

public class TServerInterceptor implements InstanceConstructorInterceptor {
    private static final ILog logger = LogManager.getLogger(TServerInterceptor.class);

    @Override
    public void onConstruct(final EnhancedInstance instance, final Object[] arguments) {
        try {
            // hijack inputProtocolFactory_ to carry CarrierItems
            Field inputProtocolFactoryField = TServer.class.getDeclaredField("inputProtocolFactory_");
            TServer server = (TServer) instance;
            inputProtocolFactoryField.setAccessible(true);
            final TProtocolFactory factory = (TProtocolFactory) inputProtocolFactoryField.get(server);
            inputProtocolFactoryField.set(server, (TProtocolFactory) transport -> {
                return new ServerInProtocolWrapper(factory.getProtocol(transport));
            });
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("Hijack field 'inputProtocolFactory_' failed.", e);
        }
    }
}
