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

package org.apache.skywalking.oap.server.analyzer.event;

import org.apache.skywalking.oap.server.analyzer.event.listener.EventRecordAnalyzerListener;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class EventAnalyzerModuleProvider extends ModuleProvider {

    private EventAnalyzerServiceImpl analysisService;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return EventAnalyzerModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return new EventAnalyzerModuleConfig();
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        analysisService = new EventAnalyzerServiceImpl(getManager());
        registerServiceImplementation(EventAnalyzerService.class, analysisService);
    }

    @Override
    public void start() throws ModuleStartException {
        getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(OALEngineLoaderService.class)
                    .load(EventOALDefine.INSTANCE);

        analysisService.add(new EventRecordAnalyzerListener.Factory(getManager()));
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME
        };
    }

}
