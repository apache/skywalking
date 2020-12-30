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

package org.apache.skywalking.oap.server.storage.plugin.influxdb;

import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;

@Getter
@Builder
@AllArgsConstructor
public class TableMetaInfo {
    private static final Map<String, TableMetaInfo> TABLES = new HashMap<>();

    private final Map<String, String> storageAndColumnMap;
    private final Map<String, String> storageAndTagMap;
    private final Model model;

    public static void addModel(Model model) {
        final List<ModelColumn> columns = model.getColumns();
        final Map<String, String> storageAndTagMap = Maps.newHashMap();
        final Map<String, String> storageAndColumnMap = Maps.newHashMap();
        columns.forEach(column -> {
            ColumnName columnName = column.getColumnName();
            storageAndColumnMap.put(columnName.getStorageName(), columnName.getName());
        });

        storageAndTagMap.put(InstanceTraffic.NAME, InfluxConstants.TagName.NAME);
        if (model.getName().endsWith("_traffic")) {
            switch (model.getName()) {
                // instance_traffic name, service_id
                case InstanceTraffic.INDEX_NAME: {
                    storageAndTagMap.put(InstanceTraffic.NAME, InfluxConstants.TagName.NAME);
                    storageAndTagMap.put(InstanceTraffic.SERVICE_ID, InfluxConstants.TagName.SERVICE_ID);
                    break;
                }
                // endpoint_traffic service_id
                case EndpointTraffic.INDEX_NAME: {
                    storageAndTagMap.put(EndpointTraffic.SERVICE_ID, InfluxConstants.TagName.SERVICE_ID);
                    break;
                }
                // service_traffic  name, group
                case ServiceTraffic.INDEX_NAME: {
                    storageAndTagMap.put(ServiceTraffic.GROUP, InfluxConstants.TagName.SERVICE_GROUP);
                    storageAndTagMap.put(ServiceTraffic.NODE_TYPE, InfluxConstants.TagName.NODE_TYPE);
                }
            }
        } else {

            // Specifies ENTITY_ID, TIME_BUCKET, NODE_TYPE, SERVICE_ID as tag
            if (storageAndColumnMap.containsKey(Metrics.ENTITY_ID)) {
                storageAndTagMap.put(Metrics.ENTITY_ID, InfluxConstants.TagName.ENTITY_ID);
            }
            if (storageAndColumnMap.containsKey(Record.TIME_BUCKET)) {
                storageAndTagMap.put(Record.TIME_BUCKET, InfluxConstants.TagName.TIME_BUCKET);
            }
            if (storageAndColumnMap.containsKey(ServiceTraffic.NODE_TYPE)) {
                storageAndTagMap.put(ServiceTraffic.NODE_TYPE, InfluxConstants.TagName.NODE_TYPE);
            }
            if (storageAndColumnMap.containsKey(SegmentRecord.SERVICE_ID)) {
                storageAndTagMap.put(SegmentRecord.SERVICE_ID, InfluxConstants.TagName.SERVICE_ID);
            }

            // The field of SegmentRecord, tags, store as tag only. see SegmentRecord.DATA_BINARY
            if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
                storageAndColumnMap.remove(SegmentRecord.TAGS);
            }
        }

        final TableMetaInfo info = TableMetaInfo.builder()
                                          .model(model)
                                          .storageAndTagMap(storageAndTagMap)
                                          .storageAndColumnMap(storageAndColumnMap)
                                          .build();
        TABLES.put(model.getName(), info);
    }

    public static TableMetaInfo get(String moduleName) {
        return TABLES.get(moduleName);
    }

}
