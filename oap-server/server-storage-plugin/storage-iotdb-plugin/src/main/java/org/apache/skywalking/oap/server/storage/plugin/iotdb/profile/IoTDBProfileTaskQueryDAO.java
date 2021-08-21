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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.profile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
public class IoTDBProfileTaskQueryDAO implements IProfileTaskQueryDAO {
    private final IoTDBClient client;
    private final ProfileTaskRecord.Builder storageBuilder = new ProfileTaskRecord.Builder();

    public IoTDBProfileTaskQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket,
                                         Long endTimeBucket, Integer limit) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(serviceId)) {
            indexAndValueMap.put(IoTDBClient.SERVICE_ID_IDX, serviceId);
        }
        query = client.addQueryIndexValue(ProfileTaskRecord.INDEX_NAME, query, indexAndValueMap);

        query.append(" where 1=1");
        if (StringUtil.isNotEmpty(endpointName)) {
            query.append(" and ").append(ProfileTaskRecord.ENDPOINT_NAME).append(" = \"").append(endpointName).append("\"");
        }
        if (Objects.nonNull(startTimeBucket)) {
            query.append(" and ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTimeBucket));
        }
        if (Objects.nonNull(endTimeBucket)) {
            query.append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTimeBucket));
        }
        if (Objects.nonNull(limit)) {
            query.append(" limit ").append(limit);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);
        // IoTDB doesn't support the query contains "1=1" and "*" at the meantime.
        String queryString = query.toString();
        queryString = queryString.replace("1=1 and ", "");

        List<? super StorageData> storageDataList = client.filterQuery(ProfileTaskRecord.INDEX_NAME, queryString, storageBuilder);
        List<ProfileTask> profileTaskList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> profileTaskList.add(record2ProfileTask((ProfileTaskRecord) storageData)));
        return profileTaskList;
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBClient.ID_IDX, id);
        query = client.addQueryIndexValue(ProfileTaskRecord.INDEX_NAME, query, indexAndValueMap);
        query.append(" limit 1").append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProfileTaskRecord.INDEX_NAME, query.toString(), storageBuilder);
        return record2ProfileTask((ProfileTaskRecord) storageDataList.get(0));
    }

    private static ProfileTask record2ProfileTask(ProfileTaskRecord record) {
        return ProfileTask.builder()
                .id(record.id())
                .serviceId(record.getServiceId())
                .endpointName(record.getEndpointName())
                .startTime(record.getStartTime())
                .createTime(record.getCreateTime())
                .duration(record.getDuration())
                .minDurationThreshold(record.getMinDurationThreshold())
                .dumpPeriod(record.getDumpPeriod())
                .maxSamplingCount(record.getMaxSamplingCount())
                .build();
    }
}
