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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.LogState;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_ID;

public class LogQueryEs7DAO extends EsDAO implements ILogQueryDAO {
    public LogQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Logs queryLogs(String metricName, int serviceId, int serviceInstanceId, String endpointId, String traceId,
                          LogState state, String stateCode, Pagination paging, int from, int limit, long startSecondTB,
                          long endSecondTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        List<QueryBuilder> mustQueryList = boolQueryBuilder.must();

        if (startSecondTB != 0 && endSecondTB != 0) {
            mustQueryList.add(QueryBuilders.rangeQuery(Record.TIME_BUCKET).gte(startSecondTB).lte(endSecondTB));
        }

        if (serviceId != Const.NONE) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(AbstractLogRecord.SERVICE_ID, serviceId));
        }
        if (serviceInstanceId != Const.NONE) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
        }
        if (StringUtil.isNotEmpty(endpointId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(AbstractLogRecord.ENDPOINT_ID, endpointId));
        }
        if (!Strings.isNullOrEmpty(stateCode)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(AbstractLogRecord.STATUS_CODE, stateCode));
        }
        if (!Strings.isNullOrEmpty(traceId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(TRACE_ID, traceId));
        }
        if (LogState.ERROR.equals(state)) {
            boolQueryBuilder.must()
                            .add(
                                QueryBuilders.termQuery(AbstractLogRecord.IS_ERROR, BooleanUtils.booleanToValue(true)));
        } else if (LogState.SUCCESS.equals(state)) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(
                                AbstractLogRecord.IS_ERROR,
                                BooleanUtils.booleanToValue(false)
                            ));
        }

        sourceBuilder.size(limit);
        sourceBuilder.from(from);

        SearchResponse response = getClient().search(metricName, sourceBuilder);

        Logs logs = new Logs();
        logs.setTotal((int) response.getHits().getTotalHits().value);

        for (SearchHit searchHit : response.getHits().getHits()) {
            Log log = new Log();
            log.setServiceId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.SERVICE_ID));
            log.setServiceInstanceId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.SERVICE_INSTANCE_ID));
            log.setEndpointId((String) searchHit.getSourceAsMap().get(AbstractLogRecord.ENDPOINT_ID));
            log.setEndpointName((String) searchHit.getSourceAsMap().get(AbstractLogRecord.ENDPOINT_NAME));
            log.setError(BooleanUtils.valueToBoolean(((Number) searchHit.getSourceAsMap()
                                                                        .get(AbstractLogRecord.IS_ERROR)).intValue()));
            log.setStatusCode((String) searchHit.getSourceAsMap().get(AbstractLogRecord.STATUS_CODE));
            log.setContentType(ContentType.instanceOf(((Number) searchHit.getSourceAsMap()
                                                                         .get(
                                                                             AbstractLogRecord.CONTENT_TYPE)).intValue()));
            log.setContent((String) searchHit.getSourceAsMap().get(AbstractLogRecord.CONTENT));

            logs.getLogs().add(log);
        }

        return logs;
    }
}
