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
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.elasticsearch.common.Strings;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereNested;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class AlarmQuery implements IAlarmQueryDAO {
    private final InfluxClient client;

    public AlarmQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB,
                           long endTB, List<Tag> tags) throws IOException {

        WhereQueryImpl<SelectQueryImpl> recallQuery = select()
            .function("top", AlarmRecord.START_TIME, limit + from).as(AlarmRecord.START_TIME)
            .column(AlarmRecord.ID0)
            .column(AlarmRecord.ALARM_MESSAGE)
            .column(AlarmRecord.SCOPE)
            .column(AlarmRecord.TAGS_RAW_DATA)
            .from(client.getDatabase(), AlarmRecord.INDEX_NAME)
            .where();
        if (startTB > 0 && endTB > 0) {
            recallQuery.and(gte(InfluxClient.TIME, InfluxClient.timeIntervalTB(startTB)))
                       .and(lte(InfluxClient.TIME, InfluxClient.timeIntervalTB(endTB)));
        }
        if (!Strings.isNullOrEmpty(keyword)) {
            recallQuery.and(contains(AlarmRecord.ALARM_MESSAGE, keyword.replaceAll("/", "\\\\/")));
        }
        if (Objects.nonNull(scopeId)) {
            recallQuery.and(eq(AlarmRecord.SCOPE, scopeId));
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            WhereNested<WhereQueryImpl<SelectQueryImpl>> nested = recallQuery.andNested();
            for (final Tag tag : tags) {
                nested.and(contains(tag.getKey(), "'" + tag.getValue() + "'"));
            }
            nested.close();
        }

        WhereQueryImpl<SelectQueryImpl> countQuery = select().count(AlarmRecord.ID0)
                                                             .from(client.getDatabase(), AlarmRecord.INDEX_NAME)
                                                             .where();
        recallQuery.getClauses().forEach(countQuery::where);

        Query query = new Query(countQuery.getCommand() + recallQuery.getCommand());
        List<QueryResult.Result> results = client.query(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), results);
        }
        if (results.size() != 2) {
            throw new IOException("Expecting to get 2 Results, but it is " + results.size());
        }
        List<QueryResult.Series> series = results.get(1).getSeries();
        if (series == null || series.isEmpty()) {
            return new Alarms();
        }
        List<QueryResult.Series> counter = results.get(0).getSeries();
        Alarms alarms = new Alarms();
        alarms.setTotal(((Number) counter.get(0).getValues().get(0).get(1)).intValue());

        series.get(0).getValues()
              .stream()
              // re-sort by self, because of the result order by time.
              .sorted((a, b) -> Long.compare((long) b.get(1), (long) a.get(1)))
              .skip(from)
              .forEach(values -> {
                  final int sid = ((Number) values.get(4)).intValue();
                  Scope scope = Scope.Finder.valueOf(sid);

                  AlarmMessage message = new AlarmMessage();
                  message.setStartTime((long) values.get(1));
                  message.setId((String) values.get(2));
                  message.setMessage((String) values.get(3));
                  message.setScope(scope);
                  message.setScopeId(sid);
                  String dataBinaryBase64 = (String) values.get(5);
                  if (!com.google.common.base.Strings.isNullOrEmpty(dataBinaryBase64)) {
                      parserDataBinaryBase64(dataBinaryBase64, message.getTags());
                  }
                  alarms.getMsgs().add(message);
              });
        return alarms;
    }
}
