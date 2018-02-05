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

package org.apache.skywalking.apm.plugin.grpc.v1;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;

/**
 * {@link GRPCClientInterceptor} determines the returned Interceptor based on the method type. If the method type is
 * UNARY, {@link GRPCClientInterceptor} returns BlockingCallClientInterceptor, or it returns
 * StreamCallClientInterceptor.
 *
 * @author zhang xin
 */
public class GRPCClientInterceptor implements ClientInterceptor {

    @Override
    public ClientCall interceptCall(MethodDescriptor method,
        CallOptions callOptions, Channel channel) {
        if (method.getType() != MethodDescriptor.MethodType.UNARY) {
            return new StreamCallClientInterceptor(channel.newCall(method, callOptions), method, channel);
        }
        return new BlockingCallClientInterceptor(channel.newCall(method, callOptions), method, channel);
    }

}
