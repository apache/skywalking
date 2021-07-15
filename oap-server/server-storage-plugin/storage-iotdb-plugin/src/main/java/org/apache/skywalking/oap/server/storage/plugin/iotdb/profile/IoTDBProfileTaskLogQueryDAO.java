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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class IoTDBProfileTaskLogQueryDAO implements IProfileTaskLogQueryDAO {
    private final IoTDBClient client;
    private final int fetchTaskLogMaxSize;

    public IoTDBProfileTaskLogQueryDAO(IoTDBClient client, int fetchTaskLogMaxSize) {
        this.client = client;
        this.fetchTaskLogMaxSize = fetchTaskLogMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select top_k(").append(ProfileTaskLogRecord.OPERATION_TIME).append(", 'k'='")
                .append(fetchTaskLogMaxSize).append("') from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskLogRecord.INDEX_NAME);
        final List<Long> operationTimeList = client.queryWithSelect(ProfileTaskLogRecord.INDEX_NAME, query.toString());

        query = new StringBuilder();
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskLogRecord.INDEX_NAME)
                .append(" where ").append(ProfileTaskLogRecord.OPERATION_TIME).append(" in (");
        for (int i = 0; i < operationTimeList.size(); i++) {
            if (i == 0) {
                query.append(operationTimeList.get(i));
            } else {
                query.append(", ").append(operationTimeList.get(i));
            }
        }
        query.append(")");
        List<? super StorageData> storageDataList = client.queryForList(ProfileTaskLogRecord.INDEX_NAME,
                query.toString(), new ProfileTaskLogRecord.Builder());
        final List<ProfileTaskLog> profileTaskLogList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> profileTaskLogList.add(parseLog((ProfileTaskLogRecord) storageData)));

        // resort by self, because of the select query result order by time.
        profileTaskLogList.sort((a, b) -> Long.compare(b.getOperationTime(), a.getOperationTime()));
        return profileTaskLogList;
    }

    private ProfileTaskLog parseLog(ProfileTaskLogRecord record) {
        return ProfileTaskLog.builder()
                .id(record.id())
                .taskId(record.getTaskId())
                .instanceId(record.getInstanceId())
                .operationType(ProfileTaskLogOperationType.parse(record.getOperationType()))
                .operationTime(record.getOperationTime())
                .build();
    }
}
