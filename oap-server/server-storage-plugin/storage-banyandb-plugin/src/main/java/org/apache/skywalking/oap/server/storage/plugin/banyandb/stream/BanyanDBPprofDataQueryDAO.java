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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofDataQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

public class BanyanDBPprofDataQueryDAO extends AbstractBanyanDBDAO implements IPprofDataQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
        PprofProfilingDataRecord.TASK_ID,
        PprofProfilingDataRecord.INSTANCE_ID,
        PprofProfilingDataRecord.UPLOAD_TIME,
        PprofProfilingDataRecord.DATA_BINARY
    );

    public BanyanDBPprofDataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<PprofProfilingDataRecord> getByTaskIdAndInstances(String taskId,
                                                                  List<String> instanceIds) throws IOException {
        if (StringUtil.isBlank(taskId)) {
            return new ArrayList<>();
        }
        StreamQueryResponse resp = query(
            false, PprofProfilingDataRecord.INDEX_NAME, TAGS,
            new QueryBuilder<StreamQuery>() {
                @Override
                protected void apply(StreamQuery query) {
                    query.and(eq(PprofProfilingDataRecord.TASK_ID, taskId));
                    if (CollectionUtils.isNotEmpty(instanceIds)) {
                        query.and(in(PprofProfilingDataRecord.INSTANCE_ID, instanceIds));
                    }
                }
            }
        );
        List<PprofProfilingDataRecord> records = new ArrayList<>(resp.size());
        for (final RowEntity entity : resp.getElements()) {
            records.add(buildProfilingDataRecord(entity));
        }

        return records;
    }

    private PprofProfilingDataRecord buildProfilingDataRecord(RowEntity entity) {
        final PprofProfilingDataRecord.Builder builder = new PprofProfilingDataRecord.Builder();
        BanyanDBConverter.StorageToStream storageToStream = new BanyanDBConverter.StorageToStream(
            PprofProfilingDataRecord.INDEX_NAME, entity);
        return builder.storage2Entity(storageToStream);
    }
}
