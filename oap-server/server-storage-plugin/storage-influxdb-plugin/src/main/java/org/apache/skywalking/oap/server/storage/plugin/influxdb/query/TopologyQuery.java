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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.WhereNested;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class TopologyQuery implements ITopologyQueryDAO {
    private final InfluxClient client;

    public TopologyQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(DownSampling downsampling, long startTB,
                                                                          long endTB,
                                                                          List<String> serviceIds) throws IOException {
        String measurement = ServiceRelationServerSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            serviceIds
        );
        return buildServiceCalls(query, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(DownSampling downsampling, long startTB,
                                                                         long endTB,
                                                                         List<String> serviceIds) throws IOException {
        String measurement = ServiceRelationClientSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            serviceIds
        );
        return buildServiceCalls(query, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(DownSampling downsampling, long startTB,
                                                                          long endTB) throws IOException {
        String measurement = ServiceRelationServerSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            new ArrayList<>(0)
        );
        return buildServiceCalls(query, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(DownSampling downsampling, long startTB,
                                                                         long endTB) throws IOException {
        String tableName = ServiceRelationClientSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceCallsQuery(
            tableName,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            new ArrayList<>(0)
        );
        return buildServiceCalls(query, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          DownSampling downsampling,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        String measurement = ServiceInstanceRelationServerSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceInstanceCallsQuery(measurement,
                                                              startTB,
                                                              endTB,
                                                              ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
                                                              ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID,
                                                              clientServiceId, serverServiceId
        );
        return buildInstanceCalls(query, DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                          String serverServiceId,
                                                                          DownSampling downsampling,
                                                                          long startTB,
                                                                          long endTB) throws IOException {
        String measurement = ServiceInstanceRelationClientSideMetrics.INDEX_NAME;
        WhereQueryImpl query = buildServiceInstanceCallsQuery(measurement,
                                                              startTB,
                                                              endTB,
                                                              ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
                                                              ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID,
                                                              clientServiceId, serverServiceId
        );
        return buildInstanceCalls(query, DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(DownSampling downsampling,
                                                      long startTB,
                                                      long endTB,
                                                      String destEndpointId) throws IOException {
        String measurement = EndpointRelationServerSideMetrics.INDEX_NAME;

        WhereQueryImpl query = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT,
            Collections.emptyList()
        );
        query.and(eq(EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId));

        WhereQueryImpl query2 = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT,
            Collections.emptyList()
        );
        query2.and(eq(EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId));

        List<Call.CallDetail> calls = buildEndpointCalls(query, DetectPoint.SERVER);
        calls.addAll(buildEndpointCalls(query2, DetectPoint.CLIENT));
        return calls;
    }

    private WhereQueryImpl buildServiceCallsQuery(String measurement, long startTB, long endTB, String sourceCName,
                                                  String destCName, List<String> serviceIds) {
        WhereQueryImpl query = select()
            .function("distinct", Metrics.ENTITY_ID, ServiceRelationServerSideMetrics.COMPONENT_ID)
            .from(client.getDatabase(), measurement)
            .where()
            .and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB)))
            .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB)));

        if (!serviceIds.isEmpty()) {
            WhereNested whereNested = query.andNested();
            for (String id : serviceIds) {
                whereNested.or(eq(sourceCName, id))
                           .or(eq(destCName, id));
            }
            whereNested.close();
        }
        return query;
    }

    private WhereQueryImpl buildServiceInstanceCallsQuery(String measurement,
                                                          long startTB,
                                                          long endTB,
                                                          String sourceCName,
                                                          String destCName,
                                                          String sourceServiceId,
                                                          String destServiceId) {
        WhereQueryImpl query = select()
            .function("distinct", Metrics.ENTITY_ID, ServiceInstanceRelationServerSideMetrics.COMPONENT_ID)
            .from(client.getDatabase(), measurement)
            .where()
            .and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTB)))
            .and(lte(InfluxClient.TIME, InfluxClient.timeInterval(endTB)));

        StringBuilder builder = new StringBuilder("((");
        builder.append(sourceCName).append("=").append(sourceServiceId)
               .append(" and ")
               .append(destCName).append("=").append(destServiceId)
               .append(") or (")
               .append(sourceCName).append("=").append(destServiceId)
               .append(") and (")
               .append(destCName).append("=").append(sourceServiceId)
               .append("))");
        query.where(builder.toString());
        return query;
    }

    private List<Call.CallDetail> buildServiceCalls(WhereQueryImpl query,
                                                    DetectPoint detectPoint) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);

        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        List<Call.CallDetail> calls = new ArrayList<>();
        series.getValues().forEach(values -> {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = (String) values.get(1);
            int componentId = (int) values.get(2);
            call.buildFromServiceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        });
        return calls;
    }

    private List<Call.CallDetail> buildInstanceCalls(WhereQueryImpl query,
                                                     DetectPoint detectPoint) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);

        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        List<Call.CallDetail> calls = new ArrayList<>();
        series.getValues().forEach(values -> {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = (String) values.get(1);
            int componentId = (int) values.get(2);
            call.buildFromInstanceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        });
        return calls;
    }

    private List<Call.CallDetail> buildEndpointCalls(WhereQueryImpl query,
                                                     DetectPoint detectPoint) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);

        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        List<Call.CallDetail> calls = new ArrayList<>();
        series.getValues().forEach(values -> {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = (String) values.get(1);
            call.buildFromEndpointRelation(entityId, detectPoint);
            calls.add(call);
        });
        return calls;
    }
}
