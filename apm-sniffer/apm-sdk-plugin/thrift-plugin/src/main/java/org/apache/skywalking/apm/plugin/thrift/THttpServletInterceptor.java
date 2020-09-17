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
import org.apache.skywalking.apm.plugin.thrift.wrapper.ServerInProtocolWrapper;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServlet;

import java.lang.reflect.Field;

/**
 * To instrument Servlet implementation class ThriftServer.
 * {@link TServlet}
 */
public class THttpServletInterceptor implements InstanceConstructorInterceptor {
    private static final ILog logger = LogManager.getLogger(THttpServletInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance instance, Object[] allArguments) throws Throwable {
        try {
            // hijack inputProtocolFactory_ to carry CarrierItems
            Field inputProtocolFactoryField = TServlet.class.getDeclaredField("inProtocolFactory");
            TServlet server = (TServlet) instance;
            inputProtocolFactoryField.setAccessible(true);
            final TProtocolFactory factory = (TProtocolFactory) inputProtocolFactoryField.get(server);
            inputProtocolFactoryField.set(server, (TProtocolFactory) transport -> {
                return new ServerInProtocolWrapper(factory.getProtocol(transport));
            });
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("Hijack field 'inProtocolFactory' failed.", e);
        }
    }
}
