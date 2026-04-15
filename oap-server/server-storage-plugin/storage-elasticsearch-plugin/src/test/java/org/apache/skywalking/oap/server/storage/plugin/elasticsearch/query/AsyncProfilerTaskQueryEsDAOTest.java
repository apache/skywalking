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
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncProfilerTaskQueryEsDAOTest {

    @Mock
    private ElasticSearchClient client;

    private AsyncProfilerTaskQueryEsDAO dao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        dao = new AsyncProfilerTaskQueryEsDAO(client, 100);
        registerMergedTable(AsyncProfilerTaskRecord.INDEX_NAME, "records-all");
    }

    @AfterEach
    void tearDown() throws Exception {
        removeMergedTable(AsyncProfilerTaskRecord.INDEX_NAME);
    }

    @Test
    void getTaskList_inMergedTable_shouldIncludeTableNameFilter() throws Exception {
        when(client.search(anyString(), any(Search.class))).thenReturn(new SearchResponse());

        dao.getTaskList(null, null, null, null);

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture());

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME);
        assertThat(json).contains(AsyncProfilerTaskRecord.INDEX_NAME);
    }

    @Test
    void getTaskList_withServiceId_shouldIncludeServiceIdFilter() throws Exception {
        when(client.search(anyString(), any(Search.class))).thenReturn(new SearchResponse());

        dao.getTaskList("svc-1", null, null, null);

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture());

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(AsyncProfilerTaskRecord.SERVICE_ID);
    }

    @Test
    void getById_inMergedTable_shouldIncludeTableNameAndTaskIdFilter() throws Exception {
        when(client.search(anyString(), any(Search.class))).thenReturn(new SearchResponse());

        dao.getById("task-id-1");

        final ArgumentCaptor<Search> searchCaptor = ArgumentCaptor.forClass(Search.class);
        verify(client).search(anyString(), searchCaptor.capture());

        final String json = objectMapper.writeValueAsString(searchCaptor.getValue());
        assertThat(json).contains(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME);
        assertThat(json).contains(AsyncProfilerTaskRecord.INDEX_NAME);
        assertThat(json).contains(AsyncProfilerTaskRecord.TASK_ID);
    }

    @SuppressWarnings("unchecked")
    private void registerMergedTable(String logicName, String physicalName) throws Exception {
        final Field field = IndexController.LogicIndicesRegister.class.getDeclaredField("LOGIC_INDICES_CATALOG");
        field.setAccessible(true);
        ((Map<String, String>) field.get(null)).put(logicName, physicalName);
    }

    @SuppressWarnings("unchecked")
    private void removeMergedTable(String logicName) throws Exception {
        final Field field = IndexController.LogicIndicesRegister.class.getDeclaredField("LOGIC_INDICES_CATALOG");
        field.setAccessible(true);
        ((Map<String, String>) field.get(null)).remove(logicName);
    }
}
