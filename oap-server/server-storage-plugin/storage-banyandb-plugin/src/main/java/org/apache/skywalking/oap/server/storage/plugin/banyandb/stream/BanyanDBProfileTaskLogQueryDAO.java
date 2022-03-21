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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.StreamMetadata;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord} is a stream
 */
public class BanyanDBProfileTaskLogQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskLogQueryDAO {
    private final StreamMetadata profileTaskLogRecord =
            MetadataRegistry.INSTANCE.findStreamMetadata(ProfileTaskLogRecord.INDEX_NAME);

    private final int queryMaxSize;

    public BanyanDBProfileTaskLogQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        StreamQueryResponse resp = query(profileTaskLogRecord,
                ImmutableList.of(ProfileTaskLogRecord.OPERATION_TIME, ProfileTaskLogRecord.INSTANCE_ID),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(ImmutableList.of(ProfileTaskLogRecord.TASK_ID,
                                ProfileTaskLogRecord.OPERATION_TYPE));
                        query.setLimit(BanyanDBProfileTaskLogQueryDAO.this.queryMaxSize);
                    }
                });

        final LinkedList<ProfileTaskLog> tasks = new LinkedList<>();
        for (final RowEntity rowEntity : resp.getElements()) {
            tasks.add(parseTaskLog(rowEntity));
        }

        return tasks;
    }

    private ProfileTaskLog parseTaskLog(RowEntity data) {
        return ProfileTaskLog.builder()
                .id(data.getId())
                .taskId(data.getValue(StreamMetadata.TAG_FAMILY_DATA, ProfileTaskLogRecord.TASK_ID))
                .instanceId(
                        data.getValue(StreamMetadata.TAG_FAMILY_DATA, ProfileTaskLogRecord.INSTANCE_ID))
                .operationType(ProfileTaskLogOperationType.parse(
                        ((Number) data.getValue(StreamMetadata.TAG_FAMILY_DATA,
                                ProfileTaskLogRecord.OPERATION_TYPE)).intValue()))
                .operationTime(
                        ((Number) data.getValue(StreamMetadata.TAG_FAMILY_SEARCHABLE,
                                ProfileTaskLogRecord.OPERATION_TIME)).longValue())
                .build();
    }
}
