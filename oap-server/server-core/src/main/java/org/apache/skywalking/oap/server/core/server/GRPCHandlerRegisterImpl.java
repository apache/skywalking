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

package org.apache.skywalking.oap.server.core.server;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;

/**
 * @author peng-yongsheng, jian.tan
 */
public class GRPCHandlerRegisterImpl implements GRPCHandlerRegister {

    private final GRPCServer server;

    public GRPCHandlerRegisterImpl(GRPCServer server) {
        this.server = server;
    }

    @Override public void addHandler(BindableService handler) {
        server.addHandler(handler);
    }

    @Override public void addHandler(ServerServiceDefinition definition) {
        server.addHandler(definition);
    }

    @Override public void addFilter(ServerInterceptor interceptor) {

    }
}
