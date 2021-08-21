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
import org.apache.skywalking.apm.util.StringUtil;
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
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

public class IoTDBTraceQueryDAO implements ITraceQueryDAO {
    private final IoTDBClient client;
    private final StorageHashMapBuilder<SegmentRecord> storageBuilder = new SegmentRecord.Builder();

    public IoTDBTraceQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration,
                                       String serviceId, String serviceInstanceId,
                                       String endpointId, String traceId, int limit, int from,
                                       TraceState traceState, QueryOrder queryOrder, List<Tag> tags)
            throws IOException {
        StringBuilder query = new StringBuilder();
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(SegmentRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(serviceId)) {
            indexAndValueMap.put(IoTDBClient.SERVICE_ID_IDX, serviceId);
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            indexAndValueMap.put(IoTDBClient.TRACE_ID_IDX, traceId);
        }
        query = client.addQueryIndexValue(SegmentRecord.INDEX_NAME, query, indexAndValueMap);
        query.append(" where 1=1");
        if (startSecondTB != 0 && endSecondTB != 0) {
            query.append(" and ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startSecondTB));
            query.append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endSecondTB));
        }
        if (minDuration != 0) {
            query.append(" and ").append(SegmentRecord.LATENCY).append(" >= ").append(minDuration);
        }
        if (maxDuration != 0) {
            query.append(" and ").append(SegmentRecord.LATENCY).append(" <= ").append(maxDuration);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            query.append(" and ").append(SegmentRecord.SERVICE_INSTANCE_ID).append(" = \"").append(serviceInstanceId).append("\"");
        }
        if (!Strings.isNullOrEmpty(endpointId)) {
            query.append(" and ").append(SegmentRecord.ENDPOINT_ID).append(" = \"").append(endpointId).append("\"");
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                query.append(" and ").append(tag.getKey()).append(" = \"").append(tag.getValue()).append("\"");
            }
        }
        switch (traceState) {
            case ERROR:
                query.append(" and ").append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.TRUE);
                break;
            case SUCCESS:
                query.append(" and ").append(SegmentRecord.IS_ERROR).append(" = ").append(BooleanUtils.FALSE);
                break;
        }
        // IoTDB doesn't support the query contains "1=1" and "*" at the meantime.
        String queryString = query.toString().replace("1=1 and ", "");
        queryString = queryString + IoTDBClient.ALIGN_BY_DEVICE;

        TraceBrief traceBrief = new TraceBrief();
        List<? super StorageData> storageDataList = client.filterQuery(SegmentRecord.INDEX_NAME, queryString, storageBuilder);
        int limitCount = 0;
        for (int i = 0; i < storageDataList.size(); i++) {
            if (i >= from && limitCount < limit) {
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
        query.append("select * from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(SegmentRecord.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBClient.TRACE_ID_IDX, traceId);
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
