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

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.exporter.*;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * @author wusheng
 */
public class GRPCExporterProvider extends ModuleProvider {
    private GRPCExporterSetting setting;
    private GRPCExporter exporter;

    @Override public String name() {
        return "grpc";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return ExporterModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        setting = new GRPCExporterSetting();
        return setting;
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        exporter = new GRPCExporter(setting);
        this.registerServiceImplementation(MetricValuesExportService.class, exporter);
    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        exporter.setServiceInventoryCache(getManager().find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class));
        exporter.setServiceInstanceInventoryCache(getManager().find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class));
        exporter.setEndpointInventoryCache(getManager().find(CoreModule.NAME).provider().getService(EndpointInventoryCache.class));

        exporter.initSubscriptionList();
    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
