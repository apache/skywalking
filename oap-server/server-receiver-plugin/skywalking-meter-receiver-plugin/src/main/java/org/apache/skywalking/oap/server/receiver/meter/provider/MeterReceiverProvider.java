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

import java.util.List;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessContext;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.meter.module.MeterReceiverModule;
import org.apache.skywalking.oap.server.receiver.meter.provider.handler.MeterServiceHandler;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

public class MeterReceiverProvider extends ModuleProvider {

    private final MeterReceiverConfig config;

    private List<MeterConfig> meterConfigs;
    private MeterProcessContext processContext;

    public MeterReceiverProvider() {
        config = new MeterReceiverConfig();
    }

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
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        meterConfigs = MeterConfigs.loadConfig(config.getConfigPath());
    }

    @Override
    public void start() throws ServiceNotProvidedException {
        processContext = new MeterProcessContext(meterConfigs, getManager());
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        grpcHandlerRegister.addHandler(new MeterServiceHandler(processContext));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
        processContext.initMeters();
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            SharingServerModule.NAME
        };
    }
}
