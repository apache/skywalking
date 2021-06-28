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

package org.apache.skywalking.oap.server.receiver.jvm.provider;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.jvm.module.JVMModule;
import org.apache.skywalking.oap.server.receiver.jvm.provider.handler.JVMMetricReportServiceHandler;
import org.apache.skywalking.oap.server.receiver.jvm.provider.handler.JVMMetricReportServiceHandlerCompat;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class JVMModuleProvider extends ModuleProvider {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return JVMModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return null;
    }

    @Override
    public void prepare() {
    }

    @Override
    public void start() throws ModuleStartException {
        // load official analysis
        getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(OALEngineLoaderService.class)
                    .load(JVMOALDefine.INSTANCE);

        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        JVMMetricReportServiceHandler jvmMetricReportServiceHandler = new JVMMetricReportServiceHandler(getManager());
        grpcHandlerRegister.addHandler(jvmMetricReportServiceHandler);
        grpcHandlerRegister.addHandler(new JVMMetricReportServiceHandlerCompat(jvmMetricReportServiceHandler));
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