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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Management all of the meter builders.
 */
public class MeterProcessService implements IMeterProcessService {

    private final ModuleManager manager;
    private List<MetricConvert> metricConverts;

    public MeterProcessService(ModuleManager manager) {
        this.manager = manager;
    }

    public void start(List<MeterConfig> configs) {
        final MeterSystem meterSystem = manager.find(CoreModule.NAME).provider().getService(MeterSystem.class);
        this.metricConverts = configs.stream().map(c -> new MetricConvert(c, meterSystem)).collect(Collectors.toList());
    }

    /**
     * Generate a new processor when receive meter data.
     */
    @Override
    public MeterProcessor createProcessor() {
        return new MeterProcessor(this);
    }

    /**
     * Getting all converters.
     */
    public List<MetricConvert> converts() {
        return metricConverts;
    }

}
