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

package org.apache.skywalking.oap.server.configuration.etcd;

import com.google.common.collect.Sets;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
    EtcdKeysResponse.class,
    EtcdUtils.class,
    EtcdClient.class,
    URI.class
})
@PowerMockIgnore({"javax.management.*"})
public class EtcdConfigWatcherRegisterTest {

    @Before
    @Test
    public void shouldReadConfigs() throws Exception {
        final String group = "skywalking";
        final String testKey1 = "receiver-trace.default.slowDBAccessThreshold";
        final String testVal1 = "test";
        final String testKey2 = "testKey";
        final String testVal2 = "testVal";

        final EtcdServerSettings mockSettings = mock(EtcdServerSettings.class);
        when(mockSettings.getGroup()).thenReturn(group);
        mockStatic(EtcdUtils.class);

        List<URI> uris = mock(List.class);
        when(EtcdUtils.parse(any())).thenReturn(uris);

        final EtcdClient client = PowerMockito.mock(EtcdClient.class);
        whenNew(EtcdClient.class).withAnyArguments().thenReturn(client);

        String port = System.getProperty("etcd.port");
        URI uri = new URI("http://localhost:" + port);
        List<URI> urisArray = spy(ArrayList.class);
        urisArray.add(uri);
        URI[] array = urisArray.toArray(new URI[] {});
        when(uris.toArray(new URI[] {})).thenReturn(array);

        final EtcdConfigWatcherRegister mockRegister = spy(new EtcdConfigWatcherRegister(mockSettings));

        Whitebox.setInternalState(mockRegister, "client", client);
        Whitebox.setInternalState(mockRegister, "settings", mockSettings);

        final EtcdKeysResponse response = PowerMockito.mock(EtcdKeysResponse.class);
        final EtcdKeysResponse response1 = PowerMockito.mock(EtcdKeysResponse.class);

        final EtcdKeyGetRequest request = PowerMockito.mock(EtcdKeyGetRequest.class);

        when(client.get("/skywalking/receiver-trace.default.slowDBAccessThreshold")).thenReturn(request);
        when(request.waitForChange()).thenReturn(request);

        final EtcdResponsePromise<EtcdKeysResponse> promise = mock(EtcdResponsePromise.class);
        final ResponsePromise<EtcdKeysResponse> responseResponsePromise = mock(ResponsePromise.class);
        when(request.send()).thenReturn(promise);
        when(promise.get()).thenReturn(response);
        when(responseResponsePromise.get()).thenReturn(response);

        final EtcdKeysResponse.EtcdNode node = mock(EtcdKeysResponse.EtcdNode.class);
        when(response.getNode()).thenReturn(node);
        when(node.getKey()).thenReturn("/skywalking/receiver-trace.default.slowDBAccessThreshold");
        when(node.getValue()).thenReturn("test");

        final EtcdKeyGetRequest request1 = mock(EtcdKeyGetRequest.class);
        when(client.get("/skywalking/testKey")).thenReturn(request1);
        when(request1.waitForChange()).thenReturn(request1);
        final EtcdResponsePromise<EtcdKeysResponse> promise1 = mock(EtcdResponsePromise.class);
        final ResponsePromise<EtcdKeysResponse> responseResponsePromise1 = mock(ResponsePromise.class);
        when(request1.send()).thenReturn(promise1);
        when(promise1.get()).thenReturn(response1);
        when(responseResponsePromise1.get()).thenReturn(response1);

        final EtcdKeysResponse.EtcdNode node1 = mock(EtcdKeysResponse.EtcdNode.class);
        when(response1.getNode()).thenReturn(node1);
        when(node1.getKey()).thenReturn("/skywalking/testKey");
        when(node1.getValue()).thenReturn("testVal");

        final ConfigTable configTable = mockRegister.readConfig(Sets.newHashSet(testKey1, testKey2)).get();

        assertEquals(2, configTable.getItems().size());
        Map<String, String> kvs = new HashMap<>();
        for (ConfigTable.ConfigItem item : configTable.getItems()) {
            kvs.put(item.getName(), item.getValue());
        }
        assertEquals(testVal1, kvs.get(testKey1));
        assertEquals(testVal2, kvs.get(testKey2));
    }
}
