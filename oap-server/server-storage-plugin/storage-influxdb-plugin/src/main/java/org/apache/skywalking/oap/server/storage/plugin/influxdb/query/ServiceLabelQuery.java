/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class ServiceLabelQuery implements IServiceLabelDAO {
    private final InfluxClient client;

    @Override
    public List<String> queryAllLabels(String serviceId) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query = select(
                ServiceLabelRecord.LABEL
        )
                .from(client.getDatabase(), ServiceLabelRecord.INDEX_NAME)
                .where();

        query.and(eq(ServiceLabelRecord.SERVICE_ID, serviceId));

        return parseLabels(query);
    }

    private List<String> parseLabels(WhereQueryImpl<SelectQueryImpl> query) throws IOException {
        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {}, result: {}", query.getCommand(), series);
        }

        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        return series.getValues().stream().map(v -> (String) v.get(1)).collect(Collectors.toList());
    }
}