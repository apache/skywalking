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

package org.apache.skywalking.apm.plugin.grpc.v1.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import org.apache.skywalking.apm.agent.core.conf.Config;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link ClientInterceptor} determines the returned Interceptor based on the client tracing config.
 * If the peer tracing enable, {@link ClientInterceptor} returns {@link TracingClientCall}, or it returns
 * {@link SimpleTracingClientCall} for performance.
 * <p>
 * It is usefully for tracing external api which not include in your Skywalking system.
 *
 * @author zhang xin, kanro
 */
public class ClientInterceptor implements io.grpc.ClientInterceptor {
    private final Boolean defaultClientTracingEnable;
    private final Set<String> includeClientTracingPeers;
    private final Set<String> excludeClientTracingPeers;

    ClientInterceptor() {
        defaultClientTracingEnable = Config.Plugin.Grpc.DEFAULT_CLIENT_TRACING_ENABLE;
        includeClientTracingPeers = new HashSet<>(Config.Plugin.Grpc.INCLUDED_CLIENT_TRACING_PEERS);
        excludeClientTracingPeers = new HashSet<>(Config.Plugin.Grpc.EXCLUDED_CLIENT_TRACING_PEERS);
    }

    @Override
    public <REQUEST, RESPONSE> ClientCall<REQUEST, RESPONSE> interceptCall(MethodDescriptor<REQUEST, RESPONSE> method,
                                                                           CallOptions callOptions, Channel channel) {
        String peer = channel.authority();

        if (defaultClientTracingEnable) {
            if (includeClientTracingPeers.contains(peer)) {
                return new TracingClientCall<>(channel.newCall(method, callOptions), method, channel);
            }

            if (excludeClientTracingPeers.contains(peer)) {
                return new SimpleTracingClientCall<>(channel.newCall(method, callOptions), method, channel);
            }

            return new TracingClientCall<>(channel.newCall(method, callOptions), method, channel);
        } else {
            if (excludeClientTracingPeers.contains(peer)) {
                return new SimpleTracingClientCall<>(channel.newCall(method, callOptions), method, channel);
            }

            if (includeClientTracingPeers.contains(peer)) {
                return new TracingClientCall<>(channel.newCall(method, callOptions), method, channel);
            }

            return new SimpleTracingClientCall<>(channel.newCall(method, callOptions), method, channel);
        }
    }
}
