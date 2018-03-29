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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.*;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Active authentication header by
 *
 * @author wu-sheng
 */
public class AuthenticationFilter implements ClientInterceptor {
    private static final Metadata.Key<String> AUTH_HEAD_HEADER_NAME =
            Metadata.Key.of("Authentication", Metadata.ASCII_STRING_MARSHALLER);

    public static Channel build(ManagedChannel originChannel) {
        if (StringUtil.isEmpty(Config.Agent.AUTHENTICATION)) {
            return originChannel;
        }

        return ClientInterceptors.intercept(originChannel, new AuthenticationFilter());
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions options, Channel channel) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(method, options)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AUTH_HEAD_HEADER_NAME, Config.Agent.AUTHENTICATION);

                super.start(responseListener, headers);
            }
        };
    }
}
