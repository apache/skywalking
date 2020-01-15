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
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.Point;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.apache.skywalking.oap.server.core.analysis.TimeBucket.getTimestamp;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.*;

public class PointBuilder {

    public static Point fromMetrics(Model model, Metrics metrics, Map<String, Object> objectMap) throws IOException {
        Point.Builder builder = Point.measurement(model.getName());
        builder.tag(InfluxClient.TAG_ENTITY_ID, String.valueOf(objectMap.get(Metrics.ENTITY_ID)));
        builder.tag("id", metrics.id());

        Map<String, Object> fields = Maps.newHashMap();
        for (ModelColumn column : model.getColumns()) {
            Object value = objectMap.get(column.getColumnName().getName());

            if (value instanceof StorageDataType) {
                fields.put(column.getColumnName().getStorageName(),
                        ((StorageDataType) value).toStorageData());
            } else {
                fields.put(column.getColumnName().getStorageName(), value);
            }
        }
        return builder.fields(fields)
                .time(getTimestamp((long) fields.get(Metrics.TIME_BUCKET), model.getDownsampling()), TimeUnit.MILLISECONDS)
                .build();
    }

    public static Point fromRecord(Model model, Map<String, Object> objectMap, StorageData storageData) {
        Map<String, Object> fields = Maps.newHashMap();
        Object entityId = objectMap.get(Metrics.ENTITY_ID);

        for (ModelColumn column : model.getColumns()) {
            final ColumnName name = column.getColumnName();
            Object value = objectMap.get(name.getName());

            if (value instanceof StorageDataType) {
                fields.put(name.getStorageName(), ((StorageDataType) value).toStorageData());
            } else {
                fields.put(name.getStorageName(), value);
            }
        }
        Point.Builder builder = null;
        switch (model.getScopeId()) {
            case SEGMENT: {
                builder = fromSegmentRecord(model, fields);
                break;
            }
            case HTTP_ACCESS_LOG:
            case DATABASE_ACCESS:
            case DATABASE_SLOW_STATEMENT: {
                builder = fromLogRecord(model, fields);
                break;
            }
            case ALARM:
            case JAEGER_SPAN:
            case ZIPKIN_SPAN: {
                builder = Point.measurement(model.getName())
                        .fields(fields);
                break;
            }
            default: // FIXME need to throw an exception.
                return null;
        }
        if (Objects.nonNull(entityId)) {
            builder.tag(InfluxClient.TAG_ENTITY_ID, String.valueOf(entityId));
        }
        return builder.tag("id", storageData.id())
                .time(getTimestamp((long) fields.get(Record.TIME_BUCKET), model.getDownsampling()), TimeUnit.MILLISECONDS)
                .build();
    }

    public static final Point.Builder fromSegmentRecord(Model model, Map<String, Object> record) {
        String traceId = (String) record.remove(SegmentRecord.TRACE_ID);

        return Point.measurement(model.getName())
                .tag(SegmentRecord.TRACE_ID, traceId)
                .fields(record);
    }

    public static final Point.Builder fromLogRecord(Model model, Map<String, Object> record) {
        String traceId = (String) record.remove(AbstractLogRecord.TRACE_ID);
        String statusCode = (String) record.remove(AbstractLogRecord.STATUS_CODE);

        return Point.measurement(model.getName())
                .tag(AbstractLogRecord.TRACE_ID, traceId)
                .tag(AbstractLogRecord.STATUS_CODE, statusCode)
                .fields(record);
    }
}
