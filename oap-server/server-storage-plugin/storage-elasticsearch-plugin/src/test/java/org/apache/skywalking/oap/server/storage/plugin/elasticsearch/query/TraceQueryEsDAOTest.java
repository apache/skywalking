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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceQueryEsDAOTest {

    @Mock
    private ElasticSearchClient client;

    private TraceQueryEsDAO dao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        dao = new TraceQueryEsDAO(client, 100);
    }

    @Test
    void queryByTraceId_withDuration_shouldAddTimeBucketRangeFilter() throws Exception {
        when(client.search(anyString(), any(Search.class), any(SearchParams.class)))
            .thenReturn(new SearchResponse());

        dao.queryByTraceId("trace-abc", buildDuration());

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture(), any(SearchParams.class));

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(SegmentRecord.TIME_BUCKET);
        assertThat(json).contains(SegmentRecord.TRACE_ID);
    }

    @Test
    void queryByTraceId_withNullDuration_shouldNotAddTimeBucketRangeFilter() throws Exception {
        when(client.search(anyString(), any(Search.class), any(SearchParams.class)))
            .thenReturn(new SearchResponse());

        dao.queryByTraceId("trace-abc", null);

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture(), any(SearchParams.class));

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).doesNotContain(SegmentRecord.TIME_BUCKET);
    }

    @Test
    void queryBySegmentIdList_withDuration_shouldAddTimeBucketRangeFilter() throws Exception {
        when(client.search(anyString(), any(Search.class), any(SearchParams.class)))
            .thenReturn(new SearchResponse());

        dao.queryBySegmentIdList(Arrays.asList("seg-1", "seg-2"), buildDuration());

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture(), any(SearchParams.class));

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(SegmentRecord.TIME_BUCKET);
        assertThat(json).contains(SegmentRecord.SEGMENT_ID);
    }

    @Test
    void queryByTraceIdWithInstanceId_withDuration_shouldAddTimeBucketRangeFilter() throws Exception {
        when(client.search(anyString(), any(Search.class), any(SearchParams.class)))
            .thenReturn(new SearchResponse());

        dao.queryByTraceIdWithInstanceId(
            Collections.singletonList("trace-1"),
            Collections.singletonList("instance-1"),
            buildDuration()
        );

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture(), any(SearchParams.class));

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(SegmentRecord.TIME_BUCKET);
        assertThat(json).contains(SegmentRecord.TRACE_ID);
        assertThat(json).contains(SegmentRecord.SERVICE_INSTANCE_ID);
    }

    private static Duration buildDuration() {
        final Duration duration = new Duration();
        duration.setStart(new DateTime(2026, 4, 28, 14, 0).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(2026, 4, 28, 14, 30).toString("yyyy-MM-dd HHmm"));
        duration.setStep(Step.MINUTE);
        return duration;
    }
}
