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

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.Span;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IoTDBTraceQueryDAO implements ITraceQueryDAO {
    private final IoTDBClient client;

    public IoTDBTraceQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public TraceBrief queryBasicTraces(long startSecondTB, long endSecondTB, long minDuration, long maxDuration,
                                       String serviceId, String serviceInstanceId,
                                       String endpointId, String traceId, int limit, int from,
                                       TraceState traceState, QueryOrder queryOrder, List<Tag> tags)
            throws IOException {
        String orderBy = SegmentRecord.START_TIME;
        if (queryOrder == QueryOrder.BY_DURATION) {
            orderBy = SegmentRecord.LATENCY;
        }
        // TODO How to deal with tags
        return null;
    }

    @Override
    public List<SegmentRecord> queryByTraceId(String traceId) throws IOException {
        String query = "select " +
                SegmentRecord.SEGMENT_ID + ", " +
                SegmentRecord.TRACE_ID + ", " +
                SegmentRecord.SERVICE_ID + ", " +
                SegmentRecord.SERVICE_INSTANCE_ID + ", " +
                SegmentRecord.ENDPOINT_NAME + ", " +
                SegmentRecord.START_TIME + ", " +
                SegmentRecord.LATENCY + ", " +
                SegmentRecord.IS_ERROR + ", " +
                SegmentRecord.DATA_BINARY + " from " +
                client.getStorageGroup() + IoTDBClient.DOT + SegmentRecord.INDEX_NAME +
                " where " + SegmentRecord.TRACE_ID + " = " + traceId;

        List<? super StorageData> storageDataList = client.queryForList(SegmentRecord.INDEX_NAME, query, new SegmentRecord.Builder());
        List<SegmentRecord> segmentRecords = new ArrayList<>(storageDataList.size());
        storageDataList.forEach(storageData -> segmentRecords.add((SegmentRecord) storageData));
//        SessionPool sessionPool = client.getSessionPool();
//        SessionDataSetWrapper wrapper = null;
//        try {
//            if (sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + SegmentRecord.INDEX_NAME)) {
//                return segmentRecords;
//            }
//            wrapper = sessionPool.executeQueryStatement(query.toString());
//            while (wrapper.hasNext()) {
//                SegmentRecord segmentRecord = new SegmentRecord();
//                RowRecord rowRecord = wrapper.next();
//                List<Field> fields = rowRecord.getFields();
//                segmentRecord.setSegmentId(fields.get(1).getStringValue());
//                segmentRecord.setTraceId(fields.get(2).getStringValue());
//                segmentRecord.setServiceId(fields.get(3).getStringValue());
//                segmentRecord.setServiceInstanceId(fields.get(4).getStringValue());
//                segmentRecord.setEndpointName(fields.get(5).getStringValue());
//                segmentRecord.setStartTime(fields.get(6).getLongV());
//                segmentRecord.setEndTime(fields.get(7).getLongV());
//                segmentRecord.setLatency(fields.get(8).getIntV());
//                segmentRecord.setIsError(fields.get(9).getIntV());
//                String dataBinaryBase64 = fields.get(10).getStringValue();
//                if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
//                    segmentRecord.setDataBinary(Base64.getDecoder().decode(dataBinaryBase64));
//                }
//                segmentRecord.setVersion(fields.get(11).getIntV());
//                segmentRecords.add(segmentRecord);
//            }
//        } catch (IoTDBConnectionException | StatementExecutionException e) {
//            throw new IOException(e);
//        } finally {
//            sessionPool.closeResultSet(wrapper);
//        }
        return segmentRecords;
    }

    @Override
    public List<Span> doFlexibleTraceQuery(String traceId) {
        return Collections.emptyList();
    }
}
