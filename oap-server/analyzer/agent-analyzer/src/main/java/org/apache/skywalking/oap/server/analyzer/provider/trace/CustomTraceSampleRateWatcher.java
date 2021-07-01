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
 */

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.yaml.ClassFilterConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.isNull;

@Slf4j
public class CustomTraceSampleRateWatcher extends ConfigChangeWatcher {
    private final AtomicReference<String> settingsString;

    private volatile Map<String, ServiceInfo> serviceSampleRates = Collections.emptyMap();

    public CustomTraceSampleRateWatcher(ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "custom-trace-sample-rate");
        this.settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        final ServiceInfos defaultConfigs = parseFromFile("custom-trace-sample-rate.yml");
        log.info("Default configured custom-trace-sample-rate: {}", defaultConfigs);
        onUpdate(defaultConfigs);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("[custom-trace-sample-rate] Updating using new static config: {}", config);
        }
        this.settingsString.set(config);
        onUpdate(parseFromYml(config));
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting("");
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return settingsString.get();
    }

    public ServiceInfo getSample(String serviceName) {
        return serviceSampleRates.get(serviceName);
    }

    private void onUpdate(final ServiceInfos serviceInfos) {
        log.info("Updating custom-trace-sample-rate with: {}", serviceInfos);
        if (isNull(serviceInfos)) {
            serviceSampleRates = Collections.emptyMap();
        } else {
            serviceSampleRates = StreamSupport.stream(serviceInfos.spliterator(), false)
                    .collect(Collectors.toMap(ServiceInfo::getName, Function
                            .identity()));
        }
    }

    private ServiceInfos parseFromFile(final String file) {
        try {
            final Reader reader = ResourceUtils.read(file);
            return new Yaml(new ClassFilterConstructor(new Class[]{
                    ServiceInfos.class,
                    ServiceInfo.class,
            }))
                    .loadAs(reader, ServiceInfos.class);
        } catch (FileNotFoundException e) {
            log.error("[custom-trace-sample-rate] Cannot load serviceInfos from: {}", file, e);
        }
        return ServiceInfos.EMPTY;
    }

    private ServiceInfos parseFromYml(final String ymlContent) {
        try {
            return new Yaml(new ClassFilterConstructor(new Class[]{
                    ServiceInfos.class,
                    ServiceInfo.class
            }))
                    .loadAs(ymlContent, ServiceInfos.class);
        } catch (Exception e) {
            log.error("[custom-trace-sample-rate] Failed to parse yml content as serviceInfos: \n{}", ymlContent, e);
        }
        return ServiceInfos.EMPTY;
    }

    @Getter
    @Setter
    @ToString
    public static class ServiceInfo {
        private String name;
        // service latitude
        private AtomicReference<Integer> sampleRate;
        private AtomicReference<Integer> duration;
    }

    @ToString
    public static class ServiceInfos implements Iterable<ServiceInfo> {
        static final ServiceInfos EMPTY = new ServiceInfos();

        @Getter
        @Setter
        private Collection<ServiceInfo> services;

        ServiceInfos() {
            services = new ArrayList<>();
        }

        @Override
        public Iterator<ServiceInfo> iterator() {
            return services.iterator();
        }
    }

}
