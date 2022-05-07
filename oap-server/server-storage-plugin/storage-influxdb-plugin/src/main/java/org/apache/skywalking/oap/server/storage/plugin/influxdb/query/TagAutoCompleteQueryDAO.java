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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
@RequiredArgsConstructor
public class TagAutoCompleteQueryDAO implements ITagAutoCompleteQueryDAO {
    private final InfluxClient client;

    @Override
    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final long startSecondTB,
                                                final long endSecondTB) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .function("distinct", TagAutocompleteData.TAG_KEY)
            .from(client.getDatabase(), TagAutocompleteData.INDEX_NAME)
            .where();
        appendTagAutocompleteCondition(tagType, startSecondTB, endSecondTB, query);

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptySet();
        }
        Set<String> tagKeys = new HashSet<>();
        for (List<Object> values : series.getValues()) {
            String tagKey = (String) values.get(1);
            tagKeys.add(tagKey);
        }

        return tagKeys;
    }

    @Override
    public Set<String> queryTagAutocompleteValues(final TagType tagType,
                                                  final String tagKey,
                                                  final int limit,
                                                  final long startSecondTB,
                                                  final long endSecondTB) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .column(TagAutocompleteData.TAG_VALUE)
            .from(client.getDatabase(), TagAutocompleteData.INDEX_NAME)
            .where();
        query.limit(limit);
        query.and(eq(TagAutocompleteData.TAG_KEY, tagKey));
        appendTagAutocompleteCondition(tagType, startSecondTB, endSecondTB, query);
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptySet();
        }
        Set<String> tagValues = new HashSet<>();
        for (List<Object> values : series.getValues()) {
            String tagValue = (String) values.get(1);
            tagValues.add(tagValue);
        }

        return tagValues;
    }

    private void appendTagAutocompleteCondition(final TagType tagType,
                                                final long startSecondTB,
                                                final long endSecondTB,
                                                final WhereQueryImpl<SelectQueryImpl> query) {
        query.and(eq(TagAutocompleteData.TAG_TYPE, tagType.name()));

        long startMinTB = startSecondTB / 100;
        long endMinTB = endSecondTB / 100;
        if (startMinTB > 0) {
            query.and(gte(TagAutocompleteData.TIME_BUCKET, startMinTB));
        }
        if (endMinTB > 0) {
            query.and(lte(TagAutocompleteData.TIME_BUCKET, endMinTB));
        }
    }
}
