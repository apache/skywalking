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

package org.apache.skywalking.oap.server.configuration.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigChangeWatcher;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
public class GRPCConfigurationTest {
    private GRPCConfigurationProvider provider;
    private GRPCConfigWatcherRegister register;
    private ConfigChangeWatcher singleWatcher;
    private GroupConfigChangeWatcher groupWatcher;

    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;

    @BeforeEach
    public void before() throws IOException {
        serviceRegistry = new MutableHandlerRegistry();
        final String name = UUID.randomUUID().toString();
        InProcessServerBuilder serverBuilder =
                InProcessServerBuilder
                        .forName(name)
                        .fallbackHandlerRegistry(serviceRegistry);

        server = serverBuilder.build();
        server.start();

        channel = InProcessChannelBuilder.forName(name).build();

        RemoteEndpointSettings settings = new RemoteEndpointSettings();
        settings.setHost("localhost");
        settings.setPort(5678);
        settings.setPeriod(1);
        provider = new GRPCConfigurationProvider();
        register = new GRPCConfigWatcherRegister(settings);
        ConfigurationServiceGrpc.ConfigurationServiceBlockingStub blockingStub = ConfigurationServiceGrpc.newBlockingStub(channel);
        Whitebox.setInternalState(register, "stub", blockingStub);
        initWatcher();
        assertNotNull(provider);
    }

    @AfterEach
    public void after() {
        channel.shutdown();
        server.shutdown();

        try {
            channel.awaitTermination(1L, TimeUnit.MINUTES);
            server.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            channel.shutdownNow();
            channel = null;
            server.shutdownNow();
            server = null;
        }
    }

    @Test
    @Timeout(20)
    public void shouldReadUpdated() throws Exception {
        AtomicInteger dataFlag = new AtomicInteger(0);
        serviceRegistry.addService(new MockGRPCConfigService(dataFlag));
        assertNull(singleWatcher.value());
        register.registerConfigChangeWatcher(singleWatcher);
        register.start();

        for (String v = singleWatcher.value(); v == null; v = singleWatcher.value()) {
        }
        assertEquals("100", singleWatcher.value());
        //change
        dataFlag.set(1);
        TimeUnit.SECONDS.sleep(1);
        for (String v = singleWatcher.value(); v.equals("100"); v = singleWatcher.value()) {
        }
        assertEquals("300", singleWatcher.value());
        //no change
        dataFlag.set(2);
        TimeUnit.SECONDS.sleep(3);
        for (String v = singleWatcher.value(); !v.equals("300"); v = singleWatcher.value()) {
        }
        assertEquals("300", singleWatcher.value());
        //delete
        dataFlag.set(3);
        TimeUnit.SECONDS.sleep(1);
        for (String v = singleWatcher.value(); v.equals("300"); v = singleWatcher.value()) {
        }
        assertEquals("", singleWatcher.value());
    }

    @Test
    @Timeout(20)
    public void shouldReadUpdated4Group() throws Exception {
        AtomicInteger dataFlag = new AtomicInteger(0);
        serviceRegistry.addService(new MockGRPCConfigService(dataFlag));
        assertEquals("{}", groupWatcher.groupItems().toString());
        register.registerConfigChangeWatcher(groupWatcher);
        register.start();

        for (String v = groupWatcher.groupItems().get("item1");
            v == null;
            v = groupWatcher.groupItems().get("item1")) {
        }
        assertEquals("100", groupWatcher.groupItems().get("item1"));
        for (String v = groupWatcher.groupItems().get("item2");
            v == null;
            v = groupWatcher.groupItems().get("item2")) {
        }
        assertEquals("200", groupWatcher.groupItems().get("item2"));
        //change item2
        dataFlag.set(1);
        TimeUnit.SECONDS.sleep(1);
        for (String v = groupWatcher.groupItems().get("item2");
            v.equals("200");
            v = groupWatcher.groupItems().get("item2")) {
        }
        assertEquals("2000", groupWatcher.groupItems().get("item2"));
        //no change
        dataFlag.set(2);
        TimeUnit.SECONDS.sleep(3);
        assertEquals("100", groupWatcher.groupItems().get("item1"));
        assertEquals("2000", groupWatcher.groupItems().get("item2"));
        //delete item1
        dataFlag.set(3);
        TimeUnit.SECONDS.sleep(1);
        for (String v = groupWatcher.groupItems().get("item1");
            v != null;
            v = groupWatcher.groupItems().get("item1")) {
        }
        assertNull(groupWatcher.groupItems().get("item1"));
    }

    private void initWatcher() {
        singleWatcher = new ConfigChangeWatcher("test-module", provider, "testKey") {
            private volatile String testValue;

            @Override
            public void notify(ConfigChangeEvent value) {
                log.info("ConfigChangeWatcher.ConfigChangeEvent: {}", value);
                if (EventType.DELETE.equals(value.getEventType())) {
                    testValue = null;
                } else {
                    testValue = value.getNewValue();
                }
            }

            @Override
            public String value() {
                return testValue;
            }
        };

        groupWatcher = new GroupConfigChangeWatcher("test-module", provider, "testKeyGroup") {
            private final Map<String, String> config = new ConcurrentHashMap<>();

            @Override
            public void notifyGroup(Map<String, ConfigChangeEvent> groupItems) {
                log.info("GroupConfigChangeWatcher.ConfigChangeEvents: {}", groupItems);
                groupItems.forEach((groupItemName, event) -> {
                    if (EventType.DELETE.equals(event.getEventType())) {
                        config.remove(groupItemName);
                    } else {
                        config.put(groupItemName, event.getNewValue());
                    }
                });
            }

            @Override
            public Map<String, String> groupItems() {
                return config;
            }
        };
    }
}
