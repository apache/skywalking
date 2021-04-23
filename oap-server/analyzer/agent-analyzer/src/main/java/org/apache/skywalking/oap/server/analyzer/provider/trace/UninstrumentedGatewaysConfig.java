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

import static java.util.Objects.isNull;

@Slf4j
public class UninstrumentedGatewaysConfig extends ConfigChangeWatcher {
    private final AtomicReference<String> settingsString;

    private volatile Map<String, GatewayInstanceInfo> gatewayInstanceKeyedByAddress = Collections.emptyMap();

    public UninstrumentedGatewaysConfig(ModuleProvider provider) {
        super(AnalyzerModule.NAME, provider, "uninstrumentedGateways");
        this.settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        final GatewayInfos defaultGateways = parseGatewaysFromFile("gateways.yml");
        log.info("Default configured gateways: {}", defaultGateways);
        onGatewaysUpdated(defaultGateways);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("Updating using new static config: {}", config);
        }
        this.settingsString.set(config);
        onGatewaysUpdated(parseGatewaysFromYml(config));
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

    private void onGatewaysUpdated(final GatewayInfos gateways) {
        log.info("Updating uninstrumented gateways with: {}", gateways);
        if (isNull(gateways)) {
            gatewayInstanceKeyedByAddress = Collections.emptyMap();
        } else {
            gatewayInstanceKeyedByAddress = StreamSupport.stream(gateways.spliterator(), false)
                                                         .flatMap(instance -> instance.getInstances().stream())
                                                         .collect(
                                                             Collectors.toMap(GatewayInstanceInfo::getAddress, Function
                                                                 .identity()));
        }
    }

    public boolean isAddressConfiguredAsGateway(final String address) {
        final boolean isConfiguredAsGateway = gatewayInstanceKeyedByAddress.get(address) != null;
        if (log.isDebugEnabled()) {
            log.debug("Address [{}] is configured as gateway: {}", address, isConfiguredAsGateway);
        }
        return isConfiguredAsGateway;
    }

    private GatewayInfos parseGatewaysFromFile(final String file) {
        try {
            final Reader reader = ResourceUtils.read(file);
            return new Yaml(new ClassFilterConstructor(new Class[] {
                GatewayInfos.class,
                GatewayInfo.class,
                GatewayInstanceInfo.class,
                }))
                .loadAs(reader, GatewayInfos.class);
        } catch (FileNotFoundException e) {
            log.error("Cannot load gateways from: {}", file, e);
        }
        return GatewayInfos.EMPTY;
    }

    private GatewayInfos parseGatewaysFromYml(final String ymlContent) {
        try {
            return new Yaml(new ClassFilterConstructor(new Class[] {
                GatewayInfos.class,
                GatewayInfo.class,
                GatewayInstanceInfo.class,
                }))
                .loadAs(ymlContent, GatewayInfos.class);
        } catch (Exception e) {
            log.error("Failed to parse yml content as gateways: \n{}", ymlContent, e);
        }
        return GatewayInfos.EMPTY;
    }

    @Getter
    @Setter
    @ToString
    public static class GatewayInfo {
        private String name;

        private List<GatewayInstanceInfo> instances;
    }

    @ToString
    public static class GatewayInfos implements Iterable<GatewayInfo> {
        static final GatewayInfos EMPTY = new GatewayInfos();

        @Getter
        @Setter
        private Collection<GatewayInfo> gateways;

        GatewayInfos() {
            gateways = new ArrayList<>();
        }

        @Override
        public Iterator<GatewayInfo> iterator() {
            return gateways.iterator();
        }
    }

    @Getter
    @Setter
    @ToString
    public static class GatewayInstanceInfo {
        private String host;

        private Integer port;

        String getAddress() {
            return getHost() + ":" + (isNull(getPort()) || getPort() <= 0 ? "80" : getPort());
        }
    }
}
