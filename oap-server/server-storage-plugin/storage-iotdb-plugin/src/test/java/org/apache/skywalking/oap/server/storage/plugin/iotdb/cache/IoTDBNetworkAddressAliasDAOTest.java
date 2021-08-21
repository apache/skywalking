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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBTableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBInsertRequest;
import org.junit.Before;
import org.junit.Test;

import static org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClientTest.retrieval;

public class IoTDBNetworkAddressAliasDAOTest {
    private IoTDBNetworkAddressAliasDAO networkAddressAliasDAO;

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        IoTDBClient client = new IoTDBClient(config);
        client.connect();

        networkAddressAliasDAO = new IoTDBNetworkAddressAliasDAO(client);

        int scopeId = 2;
        boolean record = true;
        boolean superDataset = true;
        boolean timeRelativeID = false;
        List<ModelColumn> modelColumns = new ArrayList<>();
        List<ExtraQueryIndex> extraQueryIndices = new ArrayList<>();
        retrieval(NetworkAddressAlias.class, NetworkAddressAlias.INDEX_NAME, modelColumns, extraQueryIndices, scopeId);
        Model networkAddressAliasModel = new Model(
                NetworkAddressAlias.INDEX_NAME, modelColumns, extraQueryIndices, scopeId, DownSampling.Second, record,
                superDataset, FunctionCategory.uniqueFunctionName(NetworkAddressAlias.class), timeRelativeID);
        IoTDBTableMetaInfo.addModel(networkAddressAliasModel);

        StorageHashMapBuilder<NetworkAddressAlias> networkAddressAliasBuilder = new NetworkAddressAlias.Builder();
        Map<String, Object> networkAddressAliasMap = new HashMap<>();
        networkAddressAliasMap.put("address", "address_1");
        networkAddressAliasMap.put("represent_service_id", "represent_service_id_1");
        networkAddressAliasMap.put("represent_service_instance_id", "represent_service_instance_id_1");
        networkAddressAliasMap.put(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET, 1L);
        networkAddressAliasMap.put(NetworkAddressAlias.ENTITY_ID, "entity_id_1");
        networkAddressAliasMap.put(NetworkAddressAlias.TIME_BUCKET, 2L);
        NetworkAddressAlias networkAddressAlias = networkAddressAliasBuilder.storage2Entity(networkAddressAliasMap);

        IoTDBInsertRequest request = new IoTDBInsertRequest(NetworkAddressAlias.INDEX_NAME, 1L, networkAddressAlias, networkAddressAliasBuilder);
        client.write(request);
    }

    @Test
    public void loadLastUpdate() {
        List<NetworkAddressAlias> networkAddressAliases = networkAddressAliasDAO.loadLastUpdate(1L);
        networkAddressAliases.forEach(networkAddressAlias -> {
            assert networkAddressAlias.getLastUpdateTimeBucket() >= 1L;
        });
    }
}