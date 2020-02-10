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
package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.entity.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

public class ProfileThreadSnapshotQuery implements IProfileThreadSnapshotQueryDAO {
    private final InfluxClient client;

    public ProfileThreadSnapshotQuery(InfluxClient client) {
        this.client = client;
    }

    @Override public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        WhereQueryImpl query = select(ProfileThreadSnapshotRecord.SEGMENT_ID)
            .from(client.getDatabase(), ProfileThreadSnapshotRecord.INDEX_NAME)
            .where()
            .and(eq(ProfileThreadSnapshotRecord.TASK_ID, taskId))
            .and(eq(ProfileThreadSnapshotRecord.SEQUENCE, 0));

        final LinkedList<String> segments = new LinkedList<>();
        client.queryForSingleSeries(query).getValues().forEach(values -> {
            segments.add(String.valueOf(values));
        });

        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        query = select()
            .function("bottom", SegmentRecord.START_TIME, segments.size())
            .column(SegmentRecord.SEGMENT_ID)
            .column(SegmentRecord.START_TIME)
            .column(SegmentRecord.ENDPOINT_NAME)
            .column(SegmentRecord.LATENCY)
            .column(SegmentRecord.IS_ERROR)
            .column(SegmentRecord.TRACE_ID)
            .from(client.getDatabase(), SegmentRecord.INDEX_NAME)
            .where()
            .and(contains(SegmentRecord.SEGMENT_ID, Joiner.on("|").join(segments)));

        ArrayList<BasicTrace> result = Lists.newArrayListWithCapacity(segments.size());
        client.queryForSingleSeries(query)
            .getValues()
            .stream()
            .sorted((a, b) -> Long.compare(((Number)b.get(1)).longValue(), ((Number)a.get(1)).longValue()))
            .forEach(values -> {
                BasicTrace basicTrace = new BasicTrace();

                basicTrace.setSegmentId((String)values.get(2));
                basicTrace.setStart(String.valueOf(values.get(3)));
                basicTrace.getEndpointNames().add((String)values.get(4));
                basicTrace.setDuration((int)values.get(5));
                basicTrace.setError(BooleanUtils.valueToBoolean((int)values.get(6)));
                String traceIds = (String)values.get(7);
                basicTrace.getTraceIds().add(traceIds);

                result.add(basicTrace);
            });

        return result;
    }
}
