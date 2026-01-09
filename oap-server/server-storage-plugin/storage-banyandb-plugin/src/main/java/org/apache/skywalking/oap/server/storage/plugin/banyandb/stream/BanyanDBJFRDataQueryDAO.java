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
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJFRDataQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BanyanDBJFRDataQueryDAO extends AbstractBanyanDBDAO implements IJFRDataQueryDAO {

    private static final Set<String> TAGS = ImmutableSet.of(
            JFRProfilingDataRecord.TASK_ID,
            JFRProfilingDataRecord.INSTANCE_ID,
            JFRProfilingDataRecord.EVENT_TYPE,
            JFRProfilingDataRecord.UPLOAD_TIME,
            JFRProfilingDataRecord.DATA_BINARY
    );

    public BanyanDBJFRDataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<JFRProfilingDataRecord> getByTaskIdAndInstancesAndEvent(String taskId, List<String> instanceIds, String eventType) throws IOException {
        if (StringUtil.isBlank(taskId) || StringUtil.isBlank(eventType)) {
            return new ArrayList<>();
        }
        StreamQueryResponse resp = query(false, JFRProfilingDataRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        query.and(eq(JFRProfilingDataRecord.TASK_ID, taskId));
                        query.and(eq(JFRProfilingDataRecord.EVENT_TYPE, eventType));
                        if (CollectionUtils.isNotEmpty(instanceIds)) {
                            query.and(in(JFRProfilingDataRecord.INSTANCE_ID, instanceIds));
                        }
                    }
                });
        List<JFRProfilingDataRecord> records = new ArrayList<>(resp.size());
        for (final RowEntity entity : resp.getElements()) {
            records.add(buildProfilingDataRecord(entity));
        }

        return records;
    }

    private JFRProfilingDataRecord buildProfilingDataRecord(RowEntity entity) {
        final JFRProfilingDataRecord.Builder builder = new JFRProfilingDataRecord.Builder();
        BanyanDBConverter.StorageToStream storageToStream = new BanyanDBConverter.StorageToStream(JFRProfilingDataRecord.INDEX_NAME, entity);
        return builder.storage2Entity(storageToStream);
    }
}
