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
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ESEventQueryDAOTest {

    @Mock
    private ElasticSearchClient client;

    private ESEventQueryDAO dao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        dao = new ESEventQueryDAO(client);
    }

    @Test
    void buildQuery_withLayerName_shouldFilterByTheNumericLayerValue() throws Exception {
        final EventQueryCondition condition = new EventQueryCondition();
        condition.setLayer(Layer.GENERAL.name());
        condition.setPaging(new Pagination(1, 10));

        final SearchBuilder search = dao.buildQuery(condition);
        final String json = objectMapper.writeValueAsString(search.build());

        // The layer column is stored as a number, so the filter has to carry the layer value instead of
        // its name. Sending the name makes Elasticsearch fail while building the query, before any
        // document is read: number_format_exception: For input string: "GENERAL".
        assertThat(json).contains("\"layer\":" + Layer.GENERAL.value());
        assertThat(json).doesNotContain(Layer.GENERAL.name());
    }
}
