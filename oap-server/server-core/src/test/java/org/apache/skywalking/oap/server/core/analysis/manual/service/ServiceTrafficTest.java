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

package org.apache.skywalking.oap.server.core.analysis.manual.service;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class ServiceTrafficTest {
    @Test
    public void testGrouping() {
        ServiceTraffic traffic = new ServiceTraffic();
        traffic.setName("group-name::service-name");
        traffic.setLayer(Layer.UNDEFINED);
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        new ServiceTraffic.Builder().entity2Storage(traffic, toStorage);
        final Map<String, Object> stringObjectMap = toStorage.obtain();
        Assertions.assertEquals("group-name", stringObjectMap.get(ServiceTraffic.GROUP));
    }

    @Test
    public void testNoGrouping() {
        ServiceTraffic traffic = new ServiceTraffic();
        traffic.setName("group-name:service-name:no");
        traffic.setLayer(Layer.UNDEFINED);
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        new ServiceTraffic.Builder().entity2Storage(traffic, toStorage);
        final Map<String, Object> stringObjectMap = toStorage.obtain();
        Assertions.assertNull(stringObjectMap.get(ServiceTraffic.GROUP));
    }
}
