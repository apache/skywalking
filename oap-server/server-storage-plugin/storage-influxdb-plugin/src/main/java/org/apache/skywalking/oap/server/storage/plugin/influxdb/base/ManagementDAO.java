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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ID_COLUMN;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.NAME;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class ManagementDAO implements IManagementDAO {
    private final InfluxClient client;
    private final StorageHashMapBuilder<ManagementData> storageBuilder;

    public ManagementDAO(InfluxClient client, StorageHashMapBuilder<ManagementData> storageBuilder) {
        this.client = client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(final Model model, final ManagementData managementData) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select()
            .column(ID_COLUMN).column(NAME)
            .from(client.getDatabase(), UITemplate.INDEX_NAME)
            .where(eq(ID_COLUMN, managementData.id()));
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (series != null && series.getValues().size() > 0) {
            return;
        }

        Point point = Point.measurement(UITemplate.INDEX_NAME)
                           .tag(InfluxConstants.TagName.ID_COLUMN, managementData.id())
                           .time(1L, TimeUnit.NANOSECONDS)
                           .fields(storageBuilder.entity2Storage(managementData)).build();
        client.write(point);
    }
}
