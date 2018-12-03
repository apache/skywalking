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

package org.apache.skywalking.oap.server.core;

import java.util.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.config.*;
import org.apache.skywalking.oap.server.core.query.*;
import org.apache.skywalking.oap.server.core.register.service.*;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.server.*;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.model.IModelGetter;
import org.apache.skywalking.oap.server.core.storage.model.IModelOverride;
import org.apache.skywalking.oap.server.library.module.*;

/**
 * @author peng-yongsheng
 */
public class CoreModule extends ModuleDefine {

    public static final String NAME = "core";

    public CoreModule() {
        super(NAME);
    }

    @Override public Class[] services() {
        List<Class> classes = new ArrayList<>();
        classes.add(DownsamplingConfigService.class);
        classes.add(IComponentLibraryCatalogService.class);

        addServerInterface(classes);
        addReceiverInterface(classes);
        addInsideService(classes);
        addRegisterService(classes);
        addCacheService(classes);
        addQueryService(classes);

        return classes.toArray(new Class[] {});
    }

    private void addQueryService(List<Class> classes) {
        classes.add(TopologyQueryService.class);
        classes.add(MetricQueryService.class);
        classes.add(TraceQueryService.class);
        classes.add(MetadataQueryService.class);
        classes.add(AggregationQueryService.class);
        classes.add(AlarmQueryService.class);
    }

    private void addServerInterface(List<Class> classes) {
        classes.add(GRPCHandlerRegister.class);
        classes.add(JettyHandlerRegister.class);
    }

    private void addInsideService(List<Class> classes) {
        classes.add(IModelGetter.class);
        classes.add(IModelOverride.class);
        classes.add(StreamDataClassGetter.class);
        classes.add(RemoteClientManager.class);
        classes.add(RemoteSenderService.class);
    }

    private void addRegisterService(List<Class> classes) {
        classes.add(IServiceInventoryRegister.class);
        classes.add(IServiceInstanceInventoryRegister.class);
        classes.add(IEndpointInventoryRegister.class);
        classes.add(INetworkAddressInventoryRegister.class);
    }

    private void addCacheService(List<Class> classes) {
        classes.add(ServiceInventoryCache.class);
        classes.add(ServiceInstanceInventoryCache.class);
        classes.add(EndpointInventoryCache.class);
        classes.add(NetworkAddressInventoryCache.class);
    }

    private void addReceiverInterface(List<Class> classes) {
        classes.add(SourceReceiver.class);
    }
}
