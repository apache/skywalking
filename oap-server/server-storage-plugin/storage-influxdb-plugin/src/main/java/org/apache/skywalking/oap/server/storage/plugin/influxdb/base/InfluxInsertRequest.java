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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Point;

/**
 * InfluxDB Point wrapper.
 */
public class InfluxInsertRequest implements InsertRequest, UpdateRequest {
    private final Point.Builder builder;
    private final Map<String, Object> fields = Maps.newHashMap();

    public <T extends StorageData> InfluxInsertRequest(Model model, T storageData, StorageHashMapBuilder<T> storageBuilder) {
        final Map<String, Object> objectMap = storageBuilder.entity2Storage(storageData);
        if (SegmentRecord.INDEX_NAME.equals(model.getName()) || LogRecord.INDEX_NAME.equals(model.getName())) {
            objectMap.remove(SegmentRecord.TAGS);
        }

        for (ModelColumn column : model.getColumns()) {
            final Object value = objectMap.get(column.getColumnName().getName());

            if (value instanceof StorageDataComplexObject) {
                fields.put(
                    column.getColumnName().getStorageName(),
                    ((StorageDataComplexObject) value).toStorageData()
                );
            } else {
                fields.put(column.getColumnName().getStorageName(), value);
            }
        }
        builder = Point.measurement(model.getName())
                       .addField(InfluxConstants.ID_COLUMN, storageData.id())
                       .fields(fields);
    }

    public InfluxInsertRequest time(long time, TimeUnit unit) {
        builder.time(time, unit);
        return this;
    }

    public InfluxInsertRequest addFieldAsTag(String fieldName, String tagName) {
        builder.tag(tagName, String.valueOf(fields.get(fieldName)));
        return this;
    }

    public InfluxInsertRequest tag(String key, String value) {
        builder.tag(key, value);
        return this;
    }

    public Point getPoint() {
        return builder.build();
    }
}