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

package org.apache.skywalking.oap.server.fetcher.cilium;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.fetcher.cilium.handler.CiliumFlowListener;
import org.apache.skywalking.oap.server.fetcher.cilium.handler.ServiceMetadata;
import org.apache.skywalking.oap.server.fetcher.cilium.nodes.CiliumNodeManager;
import org.apache.skywalking.oap.server.fetcher.cilium.nodes.GrpcStubBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.FieldsHelper;

import java.io.IOException;

@Slf4j
public class CiliumFetcherProvider extends ModuleProvider {
    private CiliumFetcherConfig config;

    protected String excludeRulesFile = "cilium-rules/exclude.yaml";
    protected String fieldMappingFile = "cilium-rules/metadata-service-mapping.yaml";

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return CiliumFetcherModule.class;
    }

    @Override
    public ConfigCreator<CiliumFetcherConfig> newConfigCreator() {
        return new ConfigCreator<CiliumFetcherConfig>() {
            @Override
            public Class<CiliumFetcherConfig> type() {
                return CiliumFetcherConfig.class;
            }

            @Override
            public void onInitialized(CiliumFetcherConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        // load official analysis
        getManager().find(CoreModule.NAME)
            .provider()
            .getService(OALEngineLoaderService.class)
            .load(CiliumOALDefine.INSTANCE);
        try {
            FieldsHelper.forClass(ServiceMetadata.class).init(fieldMappingFile);
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
        ExcludeRules excludeRules;
        try {
            excludeRules = ExcludeRules.loadRules(excludeRulesFile);
        } catch (IOException e) {
            throw new ModuleStartException("loading exclude rules error", e);
        }

        final CiliumNodeManager ciliumNodeManager = new CiliumNodeManager(getManager(), new GrpcStubBuilder(config), config);
        ciliumNodeManager.addListener(new CiliumFlowListener(getManager(), config, excludeRules));
        ciliumNodeManager.start();
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[]{
            CoreModule.NAME, ClusterModule.NAME
        };
    }
}
