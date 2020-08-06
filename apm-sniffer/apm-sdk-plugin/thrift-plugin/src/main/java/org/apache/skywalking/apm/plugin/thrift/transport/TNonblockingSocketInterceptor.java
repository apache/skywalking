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

package org.apache.skywalking.apm.plugin.thrift.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.thrift.transport.TNonblockingSocket;

/**
 * @see TNonblockingSocket
 */
public class TNonblockingSocketInterceptor implements InstanceConstructorInterceptor {
    private static final ILog logger = LogManager.getLogger(TNonblockingSocketInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance instance, Object[] arguments) {
        String remote = "UNKNOWN";
        SocketAddress address = (SocketAddress) arguments[2];
        if (address == null) {
            SocketChannel socket = (SocketChannel) arguments[0];
            try {
                address = socket.getRemoteAddress();
            } catch (IOException e) {
                logger.error("", e);
            }
        }

        if (address != null) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            remote = inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();
        }
        instance.setSkyWalkingDynamicField(remote);
    }
}