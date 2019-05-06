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
package org.apache.skywalking.oap.server.cluster.plugin.kubernetes.dependencies;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import org.apache.skywalking.oap.server.cluster.plugin.kubernetes.Event;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Type;
import java.util.Iterator;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming, 2019.05.02
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Watch.class, OkHttpClient.class})
@PowerMockIgnore("javax.management.*")
public class NamespacedPodListWatchTest {

    private NamespacedPodListWatch namespacedPodListWatch;

    private Watch mockWatch = mock(Watch.class);

    private static final String NAME_SPACE = "my-namespace";
    private static final String LABEL_SELECTOR = "equality-based";
    private static final String RESPONSE_TYPE = "my-type";
    private static final int WATCH_TIMEOUT_SECONDS = 3;


    @Before
    public void setUp() throws Exception {

        namespacedPodListWatch = new NamespacedPodListWatch(NAME_SPACE, LABEL_SELECTOR, WATCH_TIMEOUT_SECONDS);

        PowerMockito.mockStatic(Watch.class);
        when(Watch.createWatch(any(), any(), any())).thenReturn(mockWatch);
        Call mockCall = mock(Call.class);
        PowerMockito.whenNew(Call.class).withArguments(any(OkHttpClient.class), any(Request.class)).thenReturn(mockCall);

        namespacedPodListWatch.initOrReset();

        ArgumentCaptor<ApiClient> apiClientArgumentCaptor = ArgumentCaptor.forClass(ApiClient.class);
        ArgumentCaptor<Call> callArgumentCaptor = ArgumentCaptor.forClass(Call.class);
        ArgumentCaptor<Type> typeArgumentCaptor = ArgumentCaptor.forClass(Type.class);

        PowerMockito.verifyStatic();
        Watch.createWatch(
                apiClientArgumentCaptor.capture(),
                callArgumentCaptor.capture(),
                typeArgumentCaptor.capture());

        ApiClient apiClient = apiClientArgumentCaptor.getValue();
        Call call = callArgumentCaptor.getValue();
        Type type = typeArgumentCaptor.getValue();

        assertEquals(mockCall, call);
        assertNotNull(apiClient);
        assertNotNull(type);

    }

    @Test
    public void iterator() {
        when(mockWatch.hasNext()).thenReturn(true, true, false);
        Iterator mockIterator = mockIterator();
        when(mockWatch.iterator()).thenReturn(mockIterator);
        Iterator<Event> iterator = namespacedPodListWatch.iterator();

        assertNotNull(iterator);
        assertTrue(iterator.hasNext());

        Event event0 = iterator.next();
        assertNotNull(event0);
        validateEvent(event0, 0);

        assertTrue(iterator.hasNext());
        Event event1 = iterator.next();
        assertNotNull(event1);
        validateEvent(event1, 1);

        assertFalse(iterator.hasNext());

    }

    @Test
    public void iteratorWithEmpty() {
        Iterator iterator = mock(Iterator.class);
        when(iterator.hasNext()).thenReturn(false);
        when(mockWatch.iterator()).thenReturn(iterator);

        Iterator<Event> eventIterator = namespacedPodListWatch.iterator();
        assertFalse(eventIterator.hasNext());
    }


    private Iterator<Watch.Response<V1Pod>> mockIterator() {
        Iterator<Watch.Response<V1Pod>> iterator = mock(Iterator.class);

        when(iterator.hasNext()).thenReturn(true, true, false);
        Watch.Response response0 = mockResponse(0);
        Watch.Response response1 = mockResponse(1);

        when(iterator.next()).thenReturn(response0, response1);

        return iterator;
    }

    private Watch.Response<V1Pod> mockResponse(int i) {
        V1Pod v1Pod = new V1Pod();
        V1ObjectMeta meta = new V1ObjectMeta();
        V1PodStatus status = new V1PodStatus();
        status.setPodIP("PodIp" + i);
        meta.setUid("uid" + i);
        v1Pod.setMetadata(meta);
        v1Pod.setStatus(status);
        Watch.Response response = mock(Watch.Response.class);
        response.object = v1Pod;
        response.type = RESPONSE_TYPE;
        return response;
    }

    private void validateEvent(Event event, int i) {
        String type = Whitebox.getInternalState(event, "type");
        assertEquals(RESPONSE_TYPE, type);

        String uid = Whitebox.getInternalState(event, "uid");
        assertEquals("uid" + i, uid);

        String host = Whitebox.getInternalState(event, "host");
        assertEquals("PodIp" + i, host);
    }

}