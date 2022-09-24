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

package org.apache.skywalking.oap.server.receiver.telegraf.provider;

import com.google.common.base.Splitter;
import com.linecorp.armeria.common.HttpMethod;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.receiver.telegraf.module.TelegrafReceiverModule;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.config.TelegrafConfig;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.config.TelegrafConfigs;
import org.apache.skywalking.oap.server.receiver.telegraf.provider.handler.TelegrafServiceHandler;

import java.util.Collections;
import java.util.List;

public class TelegrafReceiverProvider extends ModuleProvider {
    private List<TelegrafConfig> configs;
    private TelegrafModuleConfig moduleConfig;

    public TelegrafReceiverProvider() {
        this.moduleConfig = new TelegrafModuleConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return TelegrafReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        configs = TelegrafConfigs.loadConfigs(TelegrafModuleConfig.CONFIG_PATH,
                StringUtil.isEmpty(moduleConfig.getActiveFiles()) ? Collections.emptyList() : Splitter.on(",").splitToList(moduleConfig.getActiveFiles()));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        if (CollectionUtils.isNotEmpty(configs)) {
            HTTPHandlerRegister httpHandlerRegister = getManager().find(SharingServerModule.NAME)
                    .provider()
                    .getService(HTTPHandlerRegister.class);
            MeterSystem meterSystem = getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(MeterSystem.class);
            httpHandlerRegister.addHandler(new TelegrafServiceHandler(getManager(), meterSystem, configs),
                    Collections.singletonList(HttpMethod.POST));
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            SharingServerModule.NAME
        };
    }
}
