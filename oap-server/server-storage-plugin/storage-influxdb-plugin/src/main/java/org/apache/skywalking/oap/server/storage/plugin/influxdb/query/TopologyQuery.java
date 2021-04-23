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
import org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint.EndpointRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectSubQueryImpl;
import org.influxdb.querybuilder.WhereNested;
import org.influxdb.querybuilder.WhereSubQueryImpl;

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
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(final long startTB,
                                                                          final long endTB,
                                                                          final List<String> serviceIds) throws IOException {
        final String measurement = ServiceRelationServerSideMetrics.INDEX_NAME;
        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceCallsQuery(
            measurement,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            serviceIds
        );

        return buildServiceCalls(buildQuery(subQuery), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(final long startTB,
                                                                         final long endTB,
                                                                         List<String> serviceIds) throws IOException {
        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceCallsQuery(
            ServiceRelationClientSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            serviceIds
        );
        return buildServiceCalls(buildQuery(subQuery), DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(final long startTB,
                                                                          final long endTB) throws IOException {
        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceCallsQuery(
            ServiceRelationServerSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            new ArrayList<>(0)
        );
        return buildServiceCalls(buildQuery(subQuery), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(final long startTB,
                                                                         final long endTB) throws IOException {
        WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceCallsQuery(
            ServiceRelationClientSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            ServiceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceRelationServerSideMetrics.DEST_SERVICE_ID,
            new ArrayList<>(0)
        );
        return buildServiceCalls(buildQuery(subQuery), DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(final String clientServiceId,
                                                                          final String serverServiceId,
                                                                          final long startTB,
                                                                          final long endTB) throws IOException {
        WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceInstanceCallsQuery(
            ServiceInstanceRelationServerSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            ServiceInstanceRelationServerSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationServerSideMetrics.DEST_SERVICE_ID,
            clientServiceId, serverServiceId
        );
        return buildInstanceCalls(buildQuery(subQuery), DetectPoint.SERVER);
    }

    @Override
    public List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(final String clientServiceId,
                                                                          final String serverServiceId,
                                                                          final long startTB,
                                                                          final long endTB) throws IOException {
        WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceInstanceCallsQuery(
            ServiceInstanceRelationClientSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            ServiceInstanceRelationClientSideMetrics.SOURCE_SERVICE_ID,
            ServiceInstanceRelationClientSideMetrics.DEST_SERVICE_ID,
            clientServiceId, serverServiceId
        );
        return buildInstanceCalls(buildQuery(subQuery), DetectPoint.CLIENT);
    }

    @Override
    public List<Call.CallDetail> loadEndpointRelation(final long startTB,
                                                      final long endTB,
                                                      final String destEndpointId) throws IOException {
        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = buildServiceCallsQuery(
            EndpointRelationServerSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT,
            Collections.emptyList()
        );
        subQuery.and(eq(EndpointRelationServerSideMetrics.DEST_ENDPOINT, destEndpointId));

        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery2 = buildServiceCallsQuery(
            EndpointRelationServerSideMetrics.INDEX_NAME,
            startTB,
            endTB,
            EndpointRelationServerSideMetrics.SOURCE_ENDPOINT,
            EndpointRelationServerSideMetrics.DEST_ENDPOINT,
            Collections.emptyList()
        );
        subQuery2.and(eq(EndpointRelationServerSideMetrics.SOURCE_ENDPOINT, destEndpointId));

        final List<Call.CallDetail> calls = buildEndpointCalls(buildQuery(subQuery), DetectPoint.SERVER);
        calls.addAll(buildEndpointCalls(buildQuery(subQuery), DetectPoint.CLIENT));
        return calls;
    }

    private WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> buildServiceCallsQuery(
        final String measurement,
        final long startTB,
        final long endTB,
        final String sourceCName,
        final String destCName,
        final List<String> serviceIds) {

        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = select()
            .fromSubQuery(client.getDatabase())
            .function("distinct", ServiceInstanceRelationServerSideMetrics.COMPONENT_ID)
            .as(ServiceInstanceRelationClientSideMetrics.COMPONENT_ID)
            .from(measurement)
            .where()
            .and(gte(InfluxClient.TIME, InfluxClient.timeIntervalTB(startTB)))
            .and(lte(InfluxClient.TIME, InfluxClient.timeIntervalTB(endTB)));

        if (!serviceIds.isEmpty()) {
            WhereNested<WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl>> whereNested = subQuery
                .andNested();
            for (String id : serviceIds) {
                whereNested.or(eq(sourceCName, id))
                           .or(eq(destCName, id));
            }
            whereNested.close();
        }
        return subQuery;
    }

    private WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> buildServiceInstanceCallsQuery(
        final String measurement,
        final long startTB,
        final long endTB,
        final String sourceCName,
        final String destCName,
        final String sourceServiceId,
        final String destServiceId) {

        final WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery = select()
            .fromSubQuery(client.getDatabase())
            .function("distinct", ServiceInstanceRelationServerSideMetrics.COMPONENT_ID)
            .as(ServiceInstanceRelationClientSideMetrics.COMPONENT_ID)
            .from(measurement)
            .where();

        subQuery.and(gte(InfluxClient.TIME, InfluxClient.timeIntervalTB(startTB)))
                .and(lte(InfluxClient.TIME, InfluxClient.timeIntervalTB(endTB)));

        final StringBuilder builder = new StringBuilder("((");
        builder.append(sourceCName).append("='").append(sourceServiceId)
               .append("' and ")
               .append(destCName).append("='").append(destServiceId)
               .append("') or (")
               .append(sourceCName).append("='").append(destServiceId)
               .append("') and (")
               .append(destCName).append("='").append(sourceServiceId)
               .append("'))");
        subQuery.where(builder.toString());
        subQuery.groupBy(InfluxConstants.TagName.ENTITY_ID);
        return subQuery;
    }

    private List<Call.CallDetail> buildServiceCalls(Query query,
                                                    DetectPoint detectPoint) throws IOException {
        final QueryResult.Series series = client.queryForSingleSeries(query);

        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        final List<Call.CallDetail> calls = new ArrayList<>();
        series.getValues().forEach(values -> {
            Call.CallDetail call = new Call.CallDetail();
            String entityId = String.valueOf(values.get(1));
            int componentId = ((Number) values.get(2)).intValue();
            call.buildFromServiceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        });
        return calls;
    }

    private Query buildQuery(WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> subQuery) {
        SelectQueryImpl query = select().column(InfluxConstants.TagName.ENTITY_ID)
                                        .column(ServiceInstanceRelationClientSideMetrics.COMPONENT_ID)
                                        .from(client.getDatabase());
        query.setSubQuery(subQuery.groupBy(InfluxConstants.TagName.ENTITY_ID));
        return query;
    }

    private List<Call.CallDetail> buildInstanceCalls(Query query, DetectPoint detectPoint) throws IOException {
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
            int componentId = ((Number) values.get(2)).intValue();
            call.buildFromInstanceRelation(entityId, componentId, detectPoint);
            calls.add(call);
        });
        return calls;
    }

    private List<Call.CallDetail> buildEndpointCalls(Query query,
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
