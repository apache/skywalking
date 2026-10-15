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

import java.util.Collections;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationQueryEsDAOTest {

    @Mock
    private ElasticSearchClient client;

    @BeforeEach
    void setUp() {
        IndexNames.configure(3, 1);
    }

    @AfterEach
    void tearDown() {
        IndexNames.configure(1, -1);
    }

    @Test
    void sortMetrics_shouldSearchTheIndexRolledByDayStep() {
        when(client.search(any(String[].class), any(Search.class))).thenReturn(new SearchResponse());

        final TopNCondition condition = new TopNCondition();
        condition.setName("service_resp_time");
        condition.setTopN(10);
        condition.setOrder(Order.DES);
        final Duration duration = new Duration();
        duration.setStart("2020-08-09 1010");
        duration.setEnd("2020-08-09 1020");
        duration.setStep(Step.MINUTE);

        new AggregationQueryEsDAO(client).sortMetrics(condition, "value", duration, Collections.emptyList());

        final ArgumentCaptor<String[]> indices = ArgumentCaptor.forClass(String[].class);
        verify(client).search(indices.capture(), any(Search.class));
        // With dayStep 3, the minutes of 2020-08-09 are written to the index of 2020-08-07,
        // see IndexNamesTest#writeIndex_rollsMetricsAndRecordsByDayStepAndSuperDatasetsBySuperDatasetDayStep.
        assertThat(indices.getValue()).containsExactly("service_resp_time-20200807");
    }
}
