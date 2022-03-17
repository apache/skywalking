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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
@RequiredArgsConstructor
public class IoTDBProfileTaskLogQueryDAO implements IProfileTaskLogQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<ProfileTaskLogRecord> storageBuilder = new ProfileTaskLogRecord.Builder();
    private final int fetchTaskLogMaxSize;

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, ProfileTaskLogRecord.INDEX_NAME);
        query = client.addQueryAsterisk(ProfileTaskLogRecord.INDEX_NAME, query);
        query.append(" limit ").append(fetchTaskLogMaxSize).append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(ProfileTaskLogRecord.INDEX_NAME, query.toString(), storageBuilder);
        List<ProfileTaskLogRecord> profileTaskLogRecordList = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> profileTaskLogRecordList.add((ProfileTaskLogRecord) storageData));
        // resort by self, because of the query result order by time.
        profileTaskLogRecordList.sort((ProfileTaskLogRecord r1, ProfileTaskLogRecord r2) ->
                Long.compare(r2.getOperationTime(), r1.getOperationTime()));
        List<ProfileTaskLog> profileTaskLogList = new ArrayList<>(profileTaskLogRecordList.size());
        profileTaskLogRecordList.forEach(profileTaskLogRecord -> profileTaskLogList.add(parseLog(profileTaskLogRecord)));
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
