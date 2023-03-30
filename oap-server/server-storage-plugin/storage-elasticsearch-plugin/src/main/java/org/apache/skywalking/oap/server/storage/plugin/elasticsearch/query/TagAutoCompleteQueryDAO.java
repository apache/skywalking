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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.aggregation.Aggregation;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeRangeIndexNameGenerator;

import static java.util.Objects.nonNull;

public class TagAutoCompleteQueryDAO extends EsDAO implements ITagAutoCompleteQueryDAO {
    public TagAutoCompleteQueryDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final int limit,
                                                final Duration duration) throws IOException {
        // Tags combine records by day and es rolling index by day, need search the whole day
        // just use the time to locate the physical indexes and ignore time conditions.
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        BoolQueryBuilder query = Query.bool();
        query.must(Query.term(TagAutocompleteData.TAG_TYPE, tagType.name()));
        if (IndexController.LogicIndicesRegister.isMergedTable(TagAutocompleteData.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, TagAutocompleteData.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query);
        search.aggregation(Aggregation.terms(TagAutocompleteData.TAG_KEY)
                                      .field(TagAutocompleteData.TAG_KEY)
                                      .size(limit));

        final SearchResponse response = getClient().search(
            new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(TagAutocompleteData.INDEX_NAME),
                startSecondTB, endSecondTB
            ),
            search.build()
        );
        Set<String> tagKeys = new HashSet<>();
        if (Objects.nonNull(response.getAggregations())) {
            Map<String, Object> terms =
                (Map<String, Object>) response.getAggregations().get(TagAutocompleteData.TAG_KEY);
            List<Map<String, Object>> buckets = (List<Map<String, Object>>) terms.get("buckets");

            for (Map<String, Object> bucket : buckets) {
                String tagKey = (String) bucket.get("key");
                if (StringUtil.isEmpty(tagKey)) {
                    continue;
                }
                tagKeys.add(tagKey);
            }
        }
        return tagKeys;
    }

    @Override
    public Set<String> queryTagAutocompleteValues(final TagType tagType, final String tagKey,
                                                  final int limit,
                                                  final Duration duration) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        BoolQueryBuilder query = Query.bool().must(Query.term(TagAutocompleteData.TAG_KEY, tagKey));
        query.must(Query.term(TagAutocompleteData.TAG_TYPE, tagType.name()));
        if (IndexController.LogicIndicesRegister.isMergedTable(TagAutocompleteData.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, TagAutocompleteData.INDEX_NAME));
        }
        final SearchBuilder search = Search.builder().query(query).size(limit);

        final SearchResponse response = getClient().search(
            new TimeRangeIndexNameGenerator(
                IndexController.LogicIndicesRegister.getPhysicalTableName(TagAutocompleteData.INDEX_NAME),
                startSecondTB, endSecondTB
            ),
            search.build()
        );
        Set<String> tagValues = new HashSet<>();
        for (SearchHit searchHit : response.getHits().getHits()) {
            TagAutocompleteData tag = new TagAutocompleteData.Builder().storage2Entity(
                new ElasticSearchConverter.ToEntity(TagAutocompleteData.INDEX_NAME, searchHit.getSource()));
            tagValues.add(tag.getTagValue());
        }
        return tagValues;
    }
}
