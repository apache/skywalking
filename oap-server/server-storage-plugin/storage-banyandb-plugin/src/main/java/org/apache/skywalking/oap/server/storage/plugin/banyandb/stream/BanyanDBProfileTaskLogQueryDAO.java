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

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.Element;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * {@link ProfileTaskLogRecord} is a stream
 */
public class BanyanDBProfileTaskLogQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskLogQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(ProfileTaskLogRecord.OPERATION_TIME,
            ProfileTaskLogRecord.INSTANCE_ID, ProfileTaskLogRecord.TASK_ID, ProfileTaskLogRecord.OPERATION_TYPE);

    private final int queryMaxSize;

    public BanyanDBProfileTaskLogQueryDAO(BanyanDBStorageClient client, int profileTaskQueryMaxSize) {
        super(client);
        // query log size use profile task query max size * per log count
        this.queryMaxSize = profileTaskQueryMaxSize * 50;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        StreamQueryResponse resp = query(ProfileTaskLogRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setLimit(BanyanDBProfileTaskLogQueryDAO.this.queryMaxSize);
                    }
                });

        final LinkedList<ProfileTaskLog> tasks = new LinkedList<>();
        for (final Element element : resp.getElements()) {
            tasks.add(buildProfileTaskLog(element));
        }

        return tasks;
    }

    private ProfileTaskLog buildProfileTaskLog(Element data) {
        return ProfileTaskLog.builder()
                .id(data.getId())
                .taskId(data.getTagValue(ProfileTaskLogRecord.TASK_ID))
                .instanceId(data.getTagValue(ProfileTaskLogRecord.INSTANCE_ID))
                .operationType(ProfileTaskLogOperationType.parse(((Number) data.getTagValue(ProfileTaskLogRecord.OPERATION_TYPE)).intValue()))
                .operationTime(((Number) data.getTagValue(ProfileTaskLogRecord.OPERATION_TIME)).longValue())
                .build();
    }
}
