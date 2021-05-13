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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import java.util.LinkedList;
import java.util.List;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;

public class ReceiverGRPCHandlerRegister implements GRPCHandlerRegister {

    @Setter
    private GRPCHandlerRegister grpcHandlerRegister;
    private List<ServerInterceptor> interceptors = new LinkedList<>();

    @Override
    public void addHandler(BindableService handler) {
        if (interceptors.isEmpty()) {
            grpcHandlerRegister.addHandler(handler);
        } else {
            interceptors.forEach(interceptor -> {
                grpcHandlerRegister.addHandler(handlerInterceptorBind(handler, interceptor));
            });
        }
    }

    @Override
    public void addHandler(ServerServiceDefinition definition) {
        grpcHandlerRegister.addHandler(definition);
    }

    /**
     * If you want to bind @{io.grpc.ServerInterceptor} on a handler, you must call this method before register a
     * handler.
     *
     * @param interceptor of @{io.grpc.ServerInterceptor}
     */
    @Override
    public void addFilter(ServerInterceptor interceptor) {
        this.interceptors.add(interceptor);
    }

    private ServerServiceDefinition handlerInterceptorBind(BindableService handler, ServerInterceptor interceptor) {
        return ServerInterceptors.intercept(handler, interceptor);
    }
}
