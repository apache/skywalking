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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.base;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeInteger;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.Point;

import static org.apache.skywalking.oap.server.core.analysis.TimeBucket.getTimestamp;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.DATABASE_SLOW_STATEMENT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.HTTP_ACCESS_LOG;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_LOG;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SEGMENT;

/**
 * A helper help to build a InfluxDB Point from StorageData.
 */
public class PointBuilder {
    private static final int PADDING_SIZE = 1_000_000;
    private static final AtomicRangeInteger COUNTER = new AtomicRangeInteger(0, PADDING_SIZE);

    public static Point fromMetrics(Model model, Metrics metrics, Map<String, Object> objectMap) throws IOException {
        Point.Builder builder = Point.measurement(model.getName());
        builder.tag(InfluxClient.TAG_ENTITY_ID, String.valueOf(objectMap.get(Metrics.ENTITY_ID)));

        Map<String, Object> fields = Maps.newHashMap();
        for (ModelColumn column : model.getColumns()) {
            Object value = objectMap.get(column.getColumnName().getName());

            if (value instanceof StorageDataType) {
                fields.put(
                    column.getColumnName().getStorageName(),
                    ((StorageDataType) value).toStorageData()
                );
            } else {
                fields.put(column.getColumnName().getStorageName(), value);
            }
        }
        long timeBucket = (long) fields.remove(Metrics.TIME_BUCKET);
        return builder.fields(fields)
                      .addField("id", metrics.id())
                      .tag(Metrics.TIME_BUCKET, String.valueOf(timeBucket))
                      .time(getTimestamp(timeBucket, model.getDownsampling()), TimeUnit.MILLISECONDS)
                      .build();
    }

    public static Point fromRecord(Model model, Map<String, Object> objectMap,
                                   StorageData record) throws IOException {
        Map<String, Object> fields = Maps.newHashMap();
        for (ModelColumn column : model.getColumns()) {
            final ColumnName name = column.getColumnName();
            Object value = objectMap.get(name.getName());

            if (value instanceof StorageDataType) {
                fields.put(name.getStorageName(), ((StorageDataType) value).toStorageData());
            } else {
                fields.put(name.getStorageName(), value);
            }
        }

        final Point.Builder builder = Point.measurement(model.getName()).fields(fields)
                                           .tag(Record.TIME_BUCKET, String.valueOf(fields.get(Record.TIME_BUCKET)));
        switch (model.getScopeId()) {
            case SEGMENT:
            case HTTP_ACCESS_LOG: {
                builder.tag(SegmentRecord.SERVICE_ID, String.valueOf(fields.get(SegmentRecord.SERVICE_ID)))
                       .tag(SegmentRecord.ENDPOINT_ID, String.valueOf(fields.get(SegmentRecord.ENDPOINT_ID)));
                break;
            }
            case ALARM: {
                builder.tag(AlarmRecord.SCOPE, String.valueOf(fields.get(AlarmRecord.SCOPE)));
                break;
            }
            case DATABASE_SLOW_STATEMENT: {
                builder.tag(TopN.SERVICE_ID, String.valueOf(fields.get(TopN.SERVICE_ID)));
                break;
            }
            case PROFILE_TASK_LOG: {
                builder.tag(
                    ProfileTaskLogRecord.OPERATION_TYPE,
                    String.valueOf(fields.get(ProfileTaskLogRecord.OPERATION_TYPE))
                )
                       .tag(
                           ProfileTaskLogRecord.INSTANCE_ID,
                           String.valueOf(fields.get(ProfileTaskLogRecord.INSTANCE_ID))
                       );
                break;
            }
            case PROFILE_TASK_SEGMENT_SNAPSHOT: {
                builder.tag(
                    ProfileThreadSnapshotRecord.TASK_ID,
                    String.valueOf(fields.get(ProfileThreadSnapshotRecord.TASK_ID))
                );
                break;
            }
        }

        return builder.fields(fields)
                      .addField("id", record.id())
                      .time(System.currentTimeMillis() * PADDING_SIZE + COUNTER.get(), TimeUnit.NANOSECONDS)
                      .build();
    }

}
