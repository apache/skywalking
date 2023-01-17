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

package org.apache.skywalking.oap.server.receiver.register.provider;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.receiver.register.module.RegisterModule;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.grpc.ManagementServiceGRPCHandler;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.grpc.ManagementServiceGrpcHandlerCompat;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.rest.ManagementServiceHTTPHandler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class RegisterModuleProvider extends ModuleProvider {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return RegisterModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return null;
    }

    @Override
    public void prepare() {
    }

    @Override
    public void start() {
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        ManagementServiceGRPCHandler managementServiceHTTPHandler = new ManagementServiceGRPCHandler(getManager());
        grpcHandlerRegister.addHandler(managementServiceHTTPHandler);
        grpcHandlerRegister.addHandler(new ManagementServiceGrpcHandlerCompat(managementServiceHTTPHandler));

        HTTPHandlerRegister httpHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(HTTPHandlerRegister.class);
        httpHandlerRegister.addHandler(new ManagementServiceHTTPHandler(getManager()),
                                       Collections.singletonList(HttpMethod.POST)
        );
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            SharingServerModule.NAME
        };
    }
}
