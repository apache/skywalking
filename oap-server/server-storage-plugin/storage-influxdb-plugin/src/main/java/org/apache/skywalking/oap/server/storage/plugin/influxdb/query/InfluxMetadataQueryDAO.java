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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.query.entity.Endpoint;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.MetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

/**
 * InfluxMetadataQueryDAO is a hybrid dao implementation.
 *
 * @since 7.1.0 Endpoint data is save in the Endpoint Traffic, rather than endpoint inventory(deleted). The Endpoint
 * Traffic is a time-series metrics data, so need the influxdb implementation.
 */
public class InfluxMetadataQueryDAO extends H2MetadataQueryDAO {
    private InfluxClient client;
    // 'name' is InfluxDB keyword, so escapes it
    private static final String ENDPOINT_NAME = '\"' + EndpointTraffic.NAME + '\"';

    public InfluxMetadataQueryDAO(final InfluxClient client, final JDBCHikariCPClient h2Client,
                                  final int metadataQueryMaxSize) {
        super(h2Client, metadataQueryMaxSize);
        this.client = client;
    }

    @Override
    public int numOfEndpoint() throws IOException {
        WhereQueryImpl<SelectQueryImpl> countQuery = select()
            .count(EndpointTraffic.ENTITY_ID)
            .from(client.getDatabase(), EndpointTraffic.INDEX_NAME)
            .where();
        countQuery.where(eq(EndpointTraffic.DETECT_POINT, DetectPoint.SERVER.value()));

        Query query = new Query(countQuery.getCommand());

        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (series == null) {
            return 0;
        }

        return ((Number) series.getValues().get(0).get(1)).intValue();
    }

    @Override
    public List<Endpoint> searchEndpoint(final String keyword,
                                         final int serviceId,
                                         final int limit) throws IOException {
        WhereQueryImpl<SelectQueryImpl> endpointQuery = select()
            .column(EndpointTraffic.SERVICE_ID)
            .column(ENDPOINT_NAME)
            .column(EndpointTraffic.DETECT_POINT)
            .from(client.getDatabase(), EndpointTraffic.INDEX_NAME)
            .where();
        endpointQuery.where(eq(MetricsDAO.TAG_ENDPOINT_OWNER_SERVICE, String.valueOf(serviceId)));
        if (!Strings.isNullOrEmpty(keyword)) {
            endpointQuery.where(contains(MetricsDAO.TAG_ENDPOINT_NAME, keyword.replaceAll("/", "\\\\/")));
        }
        endpointQuery.limit(limit);

        Query query = new Query(endpointQuery.getCommand());

        final QueryResult.Series series = client.queryForSingleSeries(query);

        List<Endpoint> list = new ArrayList<>(limit);
        if (series != null) {
            series.getValues().forEach(values -> {
                EndpointTraffic endpointTraffic = new EndpointTraffic();
                endpointTraffic.setServiceId((int) values.get(1));
                endpointTraffic.setName((String) values.get(2));
                endpointTraffic.setDetectPoint((int) values.get(3));

                Endpoint endpoint = new Endpoint();
                endpoint.setId(EndpointTraffic.buildId(endpointTraffic));
                endpoint.setName(endpointTraffic.getName());
                list.add(endpoint);
            });
        }
        return list;
    }
}
