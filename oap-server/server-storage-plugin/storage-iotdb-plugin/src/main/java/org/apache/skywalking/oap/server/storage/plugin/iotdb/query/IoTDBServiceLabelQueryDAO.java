/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.utils.IoTDBUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class IoTDBServiceLabelQueryDAO implements IServiceLabelDAO {
    private final IoTDBClient client;
    private final StorageBuilder<ServiceLabelRecord> storageBuilder = new ServiceLabelRecord.Builder();

    @Override
    public List<String> queryAllLabels(String serviceId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, ServiceLabelRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        IoTDBUtils.addQueryIndexValue(ServiceLabelRecord.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ServiceLabelRecord.INDEX_NAME,
                query.toString(), storageBuilder);
        return storageDataList.stream().map(t -> ((ServiceLabelRecord) t).getLabel()).collect(Collectors.toList());
    }
}