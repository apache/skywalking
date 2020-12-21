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

package org.apache.skywalking.oap.server.receiver.meter.provider;

import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.meter.module.MeterReceiverModule;
import org.apache.skywalking.oap.server.receiver.meter.provider.handler.MeterServiceHandler;
import org.apache.skywalking.oap.server.receiver.meter.provider.handler.MeterServiceHandlerCompat;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class MeterReceiverProvider extends ModuleProvider {

    private IMeterProcessService processService;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return MeterReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return null;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
    }

    @Override
    public void start() throws ServiceNotProvidedException {
        processService = getManager().find(AnalyzerModule.NAME)
                                     .provider()
                                     .getService(IMeterProcessService.class);
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        MeterServiceHandler meterServiceHandlerCompat = new MeterServiceHandler(processService);
        grpcHandlerRegister.addHandler(meterServiceHandlerCompat);
        grpcHandlerRegister.addHandler(new MeterServiceHandlerCompat(meterServiceHandlerCompat));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            AnalyzerModule.NAME,
            SharingServerModule.NAME
        };
    }
}
