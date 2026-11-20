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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The values of several tag types are read one type at a time: in one query, a value's document under each type
 * would take the limit's places from other values.
 */
@ExtendWith(MockitoExtension.class)
class TagAutoCompleteQueryDAOTagTypesTest {
    private static final List<String> LOG_SOURCES = List.of(TagType.LOG.name(), TagType.OTLP_LOG.name());

    @Mock
    private ElasticSearchClient client;

    @BeforeEach
    void setUp() {
        IndexNames.configure(3, 1);
    }

    @Test
    void aValueUnderBothTypesDoesNotHideAnother() throws Exception {
        when(client.search(any(String[].class), any(Search.class)))
            .thenReturn(response(TagType.LOG, "ERROR"), response(TagType.OTLP_LOG, "ERROR", "WARN"));

        final Set<String> values = new TagAutoCompleteQueryDAO(client).queryTagAutocompleteValues(
            LOG_SOURCES, "level", 2, null);

        assertThat(values).containsExactly("ERROR", "WARN");
        verify(client, times(2)).search(any(String[].class), any(Search.class));
    }

    @Test
    void theMergedValuesKeepTheLimit() throws Exception {
        when(client.search(any(String[].class), any(Search.class)))
            .thenReturn(response(TagType.LOG, "ERROR"), response(TagType.OTLP_LOG, "INFO"));

        assertThat(new TagAutoCompleteQueryDAO(client).queryTagAutocompleteValues(LOG_SOURCES, "level", 1, null))
            .containsExactly("ERROR");
    }

    private static SearchResponse response(final TagType tagType, final String... values) {
        final List<SearchHit> hits = new ArrayList<>();
        for (final String value : values) {
            final SearchHit hit = new SearchHit();
            hit.setSource(Map.of(
                TagAutocompleteData.TAG_KEY, "level",
                TagAutocompleteData.TAG_VALUE, value,
                TagAutocompleteData.TAG_TYPE, tagType.name(),
                TagAutocompleteData.TIME_BUCKET, 20200810L));
            hits.add(hit);
        }
        final SearchResponse response = new SearchResponse();
        response.getHits().setHits(hits);
        return response;
    }
}
