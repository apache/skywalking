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

package org.apache.skywalking.oap.server.configuration.consul;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.cache.KVCache;
import com.orbitz.consul.model.kv.ImmutableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({
    "unchecked",
    "OptionalGetWithoutIsPresent"
})
public class ConsulConfigurationWatcherRegisterTest {
    @Mock
    private ConsulConfigurationWatcherRegister register;
    private ConcurrentHashMap<String, KVCache> cacheByKey;
    private ConcurrentHashMap<String, Optional<String>> configItemKeyedByName;

    @Test
    public void shouldUpdateCachesWhenNotified() {
        cacheByKey = new ConcurrentHashMap<>();
        configItemKeyedByName = new ConcurrentHashMap<>();
        Whitebox.setInternalState(register, "cachesByKey", cacheByKey);
        Whitebox.setInternalState(register, "configItemKeyedByName", configItemKeyedByName);

        KVCache cache1 = mock(KVCache.class);
        KVCache cache2 = mock(KVCache.class);

        ArgumentCaptor<ConsulCache.Listener> listener1 = ArgumentCaptor.forClass(ConsulCache.Listener.class);
        ArgumentCaptor<ConsulCache.Listener> listener2 = ArgumentCaptor.forClass(ConsulCache.Listener.class);

        try (MockedStatic<KVCache> kvCacheMockedStatic = mockStatic(KVCache.class)) {
            kvCacheMockedStatic.when(() -> KVCache.newCache(any(), eq("key1"))).thenReturn(cache1);
            kvCacheMockedStatic.when(() -> KVCache.newCache(any(), eq("key2"))).thenReturn(cache2);

            when(register.readConfig(any(Set.class))).thenCallRealMethod();

            register.readConfig(Sets.newHashSet("key1", "key2"));

            verify(cache1).addListener(listener1.capture());
            verify(cache2).addListener(listener2.capture());

            listener1.getValue()
                    .notify(ImmutableMap.of("key1", ImmutableValue.builder()
                            .createIndex(0)
                            .modifyIndex(0)
                            .lockIndex(0)
                            .key("key1")
                            .flags(0)
                            .value(BaseEncoding.base64().encode("val1".getBytes()))
                            .build()));
            listener2.getValue()
                    .notify(ImmutableMap.of("key2", ImmutableValue.builder()
                            .createIndex(0)
                            .modifyIndex(0)
                            .lockIndex(0)
                            .key("key2")
                            .flags(0)
                            .value(BaseEncoding.base64().encode("val2".getBytes()))
                            .build()));

            assertEquals(2, configItemKeyedByName.size());
            assertEquals("val1", configItemKeyedByName.get("key1").get());
            assertEquals("val2", configItemKeyedByName.get("key2").get());
        }
    }

    @Test
    public void shouldUnsubscribeWhenKeyRemoved() {
        cacheByKey = new ConcurrentHashMap<>();
        KVCache existedCache = mock(KVCache.class);
        cacheByKey.put("existedKey", existedCache);

        configItemKeyedByName = new ConcurrentHashMap<>();
        Whitebox.setInternalState(register, "cachesByKey", cacheByKey);
        Whitebox.setInternalState(register, "configItemKeyedByName", configItemKeyedByName);

        KVCache cache1 = mock(KVCache.class);
        KVCache cache2 = mock(KVCache.class);

        ArgumentCaptor<ConsulCache.Listener> listener1 = ArgumentCaptor.forClass(ConsulCache.Listener.class);
        ArgumentCaptor<ConsulCache.Listener> listener2 = ArgumentCaptor.forClass(ConsulCache.Listener.class);

        try (MockedStatic<KVCache> kvCacheMockedStatic = mockStatic(KVCache.class)) {
            kvCacheMockedStatic.when(() -> KVCache.newCache(any(), eq("key1"))).thenReturn(cache1);
            kvCacheMockedStatic.when(() -> KVCache.newCache(any(), eq("key2"))).thenReturn(cache2);

            when(register.readConfig(any(Set.class))).thenCallRealMethod();

            register.readConfig(Sets.newHashSet("key1", "key2"));

            verify(cache1).addListener(listener1.capture());
            verify(cache2).addListener(listener2.capture());
            verify(existedCache).stop();
        }
    }
}
