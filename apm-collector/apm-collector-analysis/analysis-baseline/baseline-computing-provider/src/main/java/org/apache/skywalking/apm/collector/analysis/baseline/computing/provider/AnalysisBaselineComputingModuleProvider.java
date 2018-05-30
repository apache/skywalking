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

package org.apache.skywalking.apm.collector.analysis.baseline.computing.provider;

import org.apache.skywalking.apm.collector.analysis.baseline.computing.define.AnalysisBaselineComputingModule;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;

/**
 * The <code>AnalysisBaselineComputingModuleProvider</code> is the default implementation of {@link
 * AnalysisBaselineComputingModule}
 *
 * @author wu-sheng
 */
public class AnalysisBaselineComputingModuleProvider extends ModuleProvider {

    private static final String NAME = "default";
    private final AnalysisBaselineComputingModuleConfig config;

    public AnalysisBaselineComputingModuleProvider() {
        super();
        this.config = new AnalysisBaselineComputingModuleConfig();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends ModuleDefine> module() {
        return AnalysisBaselineComputingModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() {
    }

    @Override public void start() {
    }

    @Override public void notifyAfterCompleted() {
    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
