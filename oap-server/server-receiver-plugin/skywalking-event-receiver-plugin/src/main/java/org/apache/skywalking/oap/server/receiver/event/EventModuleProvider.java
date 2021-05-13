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

package org.apache.skywalking.oap.server.receiver.event;

import org.apache.skywalking.oap.server.analyzer.event.EventAnalyzerModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.event.grpc.EventGrpcServiceHandler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class EventModuleProvider extends ModuleProvider {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return EventModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return new EventModuleConfig();
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
    }

    @Override
    public void start() {
        final GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                                    .provider()
                                                                    .getService(GRPCHandlerRegister.class);
        final EventGrpcServiceHandler eventGRPCServiceHandler = new EventGrpcServiceHandler(getManager());
        grpcHandlerRegister.addHandler(eventGRPCServiceHandler);
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            EventAnalyzerModule.NAME,
            SharingServerModule.NAME
        };
    }

}
