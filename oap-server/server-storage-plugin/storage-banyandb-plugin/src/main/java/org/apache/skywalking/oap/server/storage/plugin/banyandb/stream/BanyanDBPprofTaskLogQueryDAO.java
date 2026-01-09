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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.library.banyandb.v1.client.Element;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

/**
 * {@link PprofTaskLogRecord} is a stream
 */
public class BanyanDBPprofTaskLogQueryDAO extends AbstractBanyanDBDAO implements IPprofTaskLogQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
        PprofTaskLogRecord.OPERATION_TIME,
        PprofTaskLogRecord.INSTANCE_ID,
        PprofTaskLogRecord.TASK_ID,
        PprofTaskLogRecord.OPERATION_TYPE
    );

    private final int queryMaxSize;

    public BanyanDBPprofTaskLogQueryDAO(BanyanDBStorageClient client, int taskQueryMaxSize) {
        super(client);
        // query log size use pprof task query max size * per log count
        this.queryMaxSize = taskQueryMaxSize * 50;
    }

    @Override
    public List<PprofTaskLog> getTaskLogList() throws IOException {
        StreamQueryResponse resp = query(
            false, PprofTaskLogRecord.INDEX_NAME, TAGS,
            new QueryBuilder<StreamQuery>() {
                @Override
                public void apply(StreamQuery query) {
                    query.setLimit(BanyanDBPprofTaskLogQueryDAO.this.queryMaxSize);
                }
            }
        );

        final LinkedList<PprofTaskLog> tasks = new LinkedList<>();
        for (final Element element : resp.getElements()) {
            tasks.add(buildPprofTaskLog(element));
        }
        return tasks;
    }

    private PprofTaskLog buildPprofTaskLog(Element data) {
        int operationTypeInt = ((Number) data.getTagValue(PprofTaskLogRecord.OPERATION_TYPE)).intValue();
        PprofTaskLogOperationType operationType = PprofTaskLogOperationType.parse(operationTypeInt);
        return PprofTaskLog.builder()
                           .id(data.getTagValue(PprofTaskLogRecord.TASK_ID))
                           .instanceId(data.getTagValue(PprofTaskLogRecord.INSTANCE_ID))
                           .operationType(operationType)
                           .operationTime(((Number) data.getTagValue(PprofTaskLogRecord.OPERATION_TIME)).longValue())
                           .build();
    }
}
