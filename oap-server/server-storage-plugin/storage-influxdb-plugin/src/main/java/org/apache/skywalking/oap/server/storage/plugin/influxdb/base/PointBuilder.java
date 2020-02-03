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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.Point;

import static org.apache.skywalking.oap.server.core.analysis.TimeBucket.getTimestamp;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.DATABASE_ACCESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.DATABASE_SLOW_STATEMENT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.HTTP_ACCESS_LOG;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.JAEGER_SPAN;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SEGMENT;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ZIPKIN_SPAN;

/**
 * A helper help to build a InfluxDB Point from StorageData.
 */
public class PointBuilder {

    public static Point fromMetrics(Model model, Metrics metrics, Map<String, Object> objectMap) throws IOException {
        Point.Builder builder = Point.measurement(model.getName());
        builder.tag(InfluxClient.TAG_ENTITY_ID, String.valueOf(objectMap.get(Metrics.ENTITY_ID)));

        Map<String, Object> fields = Maps.newHashMap();
        for (ModelColumn column : model.getColumns()) {
            Object value = objectMap.get(column.getColumnName().getName());

            if (value instanceof StorageDataType) {
                fields.put(column.getColumnName().getStorageName(),
                    ((StorageDataType)value).toStorageData());
            } else {
                fields.put(column.getColumnName().getStorageName(), value);
            }
        }
        long timeBucket = (long)fields.remove(Metrics.TIME_BUCKET);
        return builder.fields(fields)
            .addField("id", metrics.id())
            .tag(Metrics.TIME_BUCKET, String.valueOf(timeBucket))
            .time(getTimestamp(timeBucket, model.getDownsampling()), TimeUnit.MILLISECONDS)
            .build();
    }

    public static Point fromRecord(Model model, Map<String, Object> objectMap,
        StorageData storageData) throws IOException {
        Map<String, Object> fields = Maps.newHashMap();
        Object entityId = objectMap.get(Metrics.ENTITY_ID);

        for (ModelColumn column : model.getColumns()) {
            final ColumnName name = column.getColumnName();
            Object value = objectMap.get(name.getName());

            if (value instanceof StorageDataType) {
                fields.put(name.getStorageName(), ((StorageDataType)value).toStorageData());
            } else {
                fields.put(name.getStorageName(), value);
            }
        }
        Point.Builder builder = null;
        switch (model.getScopeId()) {
            case SEGMENT:
            case HTTP_ACCESS_LOG:
            case DATABASE_ACCESS:
            case DATABASE_SLOW_STATEMENT:
            case ALARM:
            case JAEGER_SPAN:
            case ZIPKIN_SPAN: {
                builder = Point.measurement(model.getName())
                    .fields(fields);
                break;
            }
            default: {
                throw new IOException("Unknown ScopeId(" + model.getScopeId() + ")");
            }
        }
        if (Objects.nonNull(entityId)) {
            builder.tag(InfluxClient.TAG_ENTITY_ID, String.valueOf(entityId));
        }
        long timeBucket = (long)fields.remove(Record.TIME_BUCKET);
        return builder.addField("id", storageData.id())
            .addField(Record.TIME_BUCKET, timeBucket)
            .time(getTimestamp(timeBucket, model.getDownsampling()), TimeUnit.MILLISECONDS)
            .build();
    }

}
