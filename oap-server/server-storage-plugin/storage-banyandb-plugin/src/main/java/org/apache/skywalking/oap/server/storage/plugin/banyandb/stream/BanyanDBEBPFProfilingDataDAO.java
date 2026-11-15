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
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBEBPFProfilingDataDAO extends AbstractBanyanDBDAO implements IEBPFProfilingDataDAO {
    /**
     * Every row of the window in one query. The rows of one upload share its time, the only order BanyanDB keeps
     * for them, so reading them in pages by offset could skip or repeat rows.
     */
    private static final int QUERY_MAX_SIZE = Integer.MAX_VALUE;
    private static final Set<String> TAGS = ImmutableSet.of(EBPFProfilingDataRecord.UPLOAD_TIME,
            EBPFProfilingDataRecord.SCHEDULE_ID,
            EBPFProfilingDataRecord.STACK_ID_LIST,
            EBPFProfilingDataRecord.TARGET_TYPE,
            EBPFProfilingDataRecord.DATA_BINARY,
            EBPFProfilingDataRecord.TASK_ID);

    public BanyanDBEBPFProfilingDataDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<EBPFProfilingDataRecord> queryData(List<String> scheduleIdList, long beginTime, long endTime) throws IOException {
        List<EBPFProfilingDataRecord> records = new ArrayList<>();
        // [beginTime, endTime): the analysis reads windows that meet at their ends, so a row on the boundary belongs
        // to the later one only. upload_time is also the element timestamp, so the window bounds the parts read.
        final TimestampRange window = new TimestampRange(beginTime, endTime);
        for (final String scheduleId : scheduleIdList) {
            final Conditions where = Conditions.create()
                    .eq(EBPFProfilingDataRecord.SCHEDULE_ID, scheduleId)
                    .gte(EBPFProfilingDataRecord.UPLOAD_TIME, beginTime)
                    .lte(EBPFProfilingDataRecord.UPLOAD_TIME, endTime - 1)
                    .limit(QUERY_MAX_SIZE);
            StreamQueryResponse resp = queryDebuggable(false, EBPFProfilingDataRecord.INDEX_NAME, TAGS, window, where);

            records.addAll(resp.getElements().stream().map(this::buildDataRecord).collect(Collectors.toList()));
        }

        return records;
    }

    private EBPFProfilingDataRecord buildDataRecord(RowEntity rowEntity) {
        final EBPFProfilingDataRecord.Builder builder = new EBPFProfilingDataRecord.Builder();
        return builder.storage2Entity(new BanyanDBConverter.StorageToStream(EBPFProfilingDataRecord.INDEX_NAME, rowEntity));
    }
}
