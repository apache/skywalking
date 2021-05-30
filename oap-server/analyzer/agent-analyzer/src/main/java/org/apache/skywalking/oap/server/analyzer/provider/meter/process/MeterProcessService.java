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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor;
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor.WatchedFile;

/**
 * Management all of the meter builders.
 */
public class MeterProcessService implements IMeterProcessService {

    private List<MetricConvert> metricConverts;

    private final MultipleFilesChangeMonitor monitor;

    private final MeterSystem meterSystem;

    public MeterProcessService(ModuleManager manager, Path configPath, List<String> configSet) {
        this.meterSystem = manager.find(CoreModule.NAME).provider().getService(MeterSystem.class);
        this.monitor = init(configPath, configSet);
    }

    private MultipleFilesChangeMonitor init(Path configPath, List<String> configSet) {
        String[] fileNames = configSet.stream()
                                      .map(f -> configPath.resolve(f).toFile().getAbsolutePath())
                                      .collect(Collectors.toList())
                                      .toArray(new String[] {});

        return new MultipleFilesChangeMonitor(
            5, // todo make it configurable
            readableContents -> {
                Map<String, MetricConvert> converterMap = metricConverts.stream()
                                                                        .collect(Collectors.toMap(
                                                                            MetricConvert::getConfigName,
                                                                            e -> e
                                                                        ));
                for (WatchedFile watchedFile : readableContents) {
                    if (!watchedFile.isChanged()) {
                        continue;
                    }
                    MetricConvert converter = converterMap.remove(watchedFile.getFilePath());
                    if (Objects.isNull(converter)) {
                        continue;
                    }
                    converter.destroy();

                    // file was deleted
                    if (Objects.isNull(watchedFile.getFileContent())) {
                        continue;
                    }

                    MeterConfig config = MeterConfigs.parse(watchedFile.getFilePath(), watchedFile.getFileContent());
                    MetricConvert convert = new MetricConvert(config, meterSystem);
                    converterMap.put(watchedFile.getFilePath(), convert);
                }

                // update metrics converters
                this.metricConverts = new ArrayList<>(converterMap.values());
            },
            fileNames
        );
    }

    public void start(List<MeterConfig> configs) {
        metricConverts = configs.stream()
                                .map(e -> new MetricConvert(e, meterSystem))
                                .collect(Collectors.toList());
        monitor.start();
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
