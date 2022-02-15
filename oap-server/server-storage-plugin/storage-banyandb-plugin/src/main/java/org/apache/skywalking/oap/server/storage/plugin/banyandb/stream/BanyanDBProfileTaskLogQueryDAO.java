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
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord} is a stream
 */
public class BanyanDBProfileTaskLogQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskLogQueryDAO {
    private final int queryMaxSize;

    public BanyanDBProfileTaskLogQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        StreamQueryResponse resp = query(ProfileTaskLogRecord.INDEX_NAME,
                ImmutableList.of(ProfileTaskLogRecord.OPERATION_TIME, ProfileTaskLogRecord.INSTANCE_ID),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(ImmutableList.of(ProfileTaskLogRecord.TASK_ID,
                                ProfileTaskLogRecord.OPERATION_TYPE));
                        query.setLimit(BanyanDBProfileTaskLogQueryDAO.this.queryMaxSize);
                    }
                });

        return resp.getElements().stream().map(new ProfileTaskLogDeserializer())
                .sorted(Comparator.comparingLong(ProfileTaskLog::getOperationTime))
                .collect(Collectors.toList());
    }

    public static class ProfileTaskLogDeserializer implements RowEntityDeserializer<ProfileTaskLog> {
        @Override
        public ProfileTaskLog apply(RowEntity row) {
            ProfileTaskLog profileTaskLog = new ProfileTaskLog();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            // searchable - operation_time
            profileTaskLog.setOperationTime(((Number) searchable.get(0).getValue()).longValue());
            // searchable - instance_id
            profileTaskLog.setInstanceId((String) searchable.get(1).getValue());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            // data - task_id
            profileTaskLog.setTaskId((String) data.get(0).getValue());
            // data - operation_type
            profileTaskLog.setOperationType(ProfileTaskLogOperationType.parse(((Number) data.get(1).getValue()).intValue()));
            return profileTaskLog;
        }
    }
}
