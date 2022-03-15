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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;

@RequiredArgsConstructor
public class IoTDBTraceQueryDAO implements ITraceQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<SegmentRecord> storageBuilder = new SegmentRecord.Builder();

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration,
                                       String serviceId, String serviceInstanceId,
                                       String endpointId, String traceId, int limit, int from,
                                       TraceState traceState, QueryOrder queryOrder, List<Tag> tags)
            throws IOException {
        StringBuilder query = new StringBuilder();
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query.append("select * from ");
        query = client.addModelPath(query, SegmentRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(serviceId)) {
            indexAndValueMap.put(IoTDBIndexes.SERVICE_ID_IDX, serviceId);
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            indexAndValueMap.put(IoTDBIndexes.TRACE_ID_IDX, traceId);
        }
        query = client.addQueryIndexValue(SegmentRecord.INDEX_NAME, query, indexAndValueMap);

        StringBuilder where = new StringBuilder(" where ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            where.append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startSecondTB)).append(" and ");
            where.append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endSecondTB)).append(" and ");
        }
        if (minDuration != 0) {
            where.append(SegmentRecord.LATENCY).append(" >= ").append(minDuration).append(" and ");
        }
        if (maxDuration != 0) {
            where.append(SegmentRecord.LATENCY).append(" <= ").append(maxDuration).append(" and ");
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            where.append(SegmentRecord.SERVICE_INSTANCE_ID).append(" = \"").append(serviceInstanceId).append("\"").append(" and ");
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
            where.append(SegmentRecord.ENDPOINT_ID).append(" = \"").append(endpointId).append("\"").append(" and ");
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                where.append(tag.getKey()).append(" = \"").append(tag.getValue()).append("\"").append(" and ");
            }
        }
        switch (traceState) {
            case ERROR:
                where.append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.TRUE).append(" and ");
                break;
            case SUCCESS:
                where.append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.FALSE).append(" and ");
                break;
        }
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        TraceBrief traceBrief = new TraceBrief();
        List<? super StorageData> storageDataList = client.filterQuery(SegmentRecord.INDEX_NAME, query.toString(), storageBuilder);
        int limitCount = 0;
        for (int i = from; i < storageDataList.size(); i++) {
            if (limitCount < limit) {
                limitCount++;
                SegmentRecord segmentRecord = (SegmentRecord) storageDataList.get(i);
                BasicTrace basicTrace = new BasicTrace();
                basicTrace.setSegmentId(segmentRecord.getSegmentId());
                basicTrace.setStart(String.valueOf(segmentRecord.getStartTime()));
                basicTrace.getEndpointNames().add(IDManager.EndpointID.analysisId(segmentRecord.getEndpointId()).getEndpointName());
                basicTrace.setDuration(segmentRecord.getLatency());
                basicTrace.setError(BooleanUtils.valueToBoolean(segmentRecord.getIsError()));
                basicTrace.getTraceIds().add(segmentRecord.getTraceId());
                traceBrief.getTraces().add(basicTrace);
            }
        }
        traceBrief.setTotal(storageDataList.size());
        // resort by self, because of the select query result order by time.
        switch (queryOrder) {
            case BY_START_TIME:
                traceBrief.getTraces().sort((BasicTrace b1, BasicTrace b2) ->
                        Long.compare(Long.parseLong(b2.getStart()), Long.parseLong(b1.getStart())));
                break;
            case BY_DURATION:
                traceBrief.getTraces().sort((BasicTrace b1, BasicTrace b2) ->
                        Integer.compare(b2.getDuration(), b1.getDuration()));
                break;
        }
        return traceBrief;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        query = client.addModelPath(query, SegmentRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.TRACE_ID_IDX, traceId);
        query = client.addQueryIndexValue(SegmentRecord.INDEX_NAME, query, indexAndValueMap);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<? super StorageData> storageDataList = client.filterQuery(SegmentRecord.INDEX_NAME, query.toString(), storageBuilder);
        List<SegmentRecord> segmentRecords = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> segmentRecords.add((SegmentRecord) storageData));
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) {
        return Collections.emptyList();
    }
}
