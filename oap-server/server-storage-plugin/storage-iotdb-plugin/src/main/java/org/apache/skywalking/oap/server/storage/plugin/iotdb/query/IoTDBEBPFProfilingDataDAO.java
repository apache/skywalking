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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class IoTDBEBPFProfilingDataDAO implements IEBPFProfilingDataDAO {
    private final IoTDBClient client;
    private final StorageBuilder<EBPFProfilingDataRecord> storageBuilder = new EBPFProfilingDataRecord.Builder();

    @Override
    public List<EBPFProfilingDataRecord> queryData(String taskId, long beginTime, long endTime) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, EBPFProfilingDataRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        query = client.addQueryIndexValue(EBPFProfilingScheduleRecord.INDEX_NAME, query, indexAndValueMap);

        StringBuilder where = new StringBuilder(" where ");
        where.append(EBPFProfilingDataRecord.TASK_ID).append(" = \"").append(taskId).append("\" and ");
        where.append(IoTDBClient.TIME).append(" >= ").append(beginTime).append(" and ");
        where.append(IoTDBClient.TIME).append(" <= ").append(endTime).append(" and ");
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(EBPFProfilingScheduleRecord.INDEX_NAME, query.toString(), storageBuilder);
        List<EBPFProfilingDataRecord> dataList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> dataList.add((EBPFProfilingDataRecord) storageData));
        return dataList;
    }
}