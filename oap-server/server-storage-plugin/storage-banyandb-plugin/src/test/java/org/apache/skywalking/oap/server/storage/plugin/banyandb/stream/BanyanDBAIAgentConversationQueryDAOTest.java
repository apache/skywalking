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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BanyanDBAIAgentConversationQueryDAOTest {
    private final List<String> emitted = new ArrayList<>();
    private final List<Model> registeredModels = new ArrayList<>();
    private BanyanDBAIAgentConversationQueryDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        final BanyanDBStorageConfig config = new BanyanDBStorageConfig();
        config.getRecordsAIAgent().setEnableColdStage(true);
        final StorageModels models = new StorageModels();
        try (MockedStatic<DefaultScopeDefine> scopes = mockStatic(DefaultScopeDefine.class)) {
            scopes.when(() -> DefaultScopeDefine.nameOf(DefaultScopeDefine.AI_AGENT_SESSION_FLOW))
                  .thenReturn("AIAgentSessionFlow");
            scopes.when(() -> DefaultScopeDefine.nameOf(DefaultScopeDefine.AI_AGENT_SESSION_DATA))
                  .thenReturn("AIAgentSessionData");
            registeredModels.add(models.add(
                AIAgentSessionFlowRecord.class, DefaultScopeDefine.AI_AGENT_SESSION_FLOW,
                new Storage(AIAgentSessionFlowRecord.INDEX_NAME, true, DownSampling.Second),
                StorageManipulationOpt.withSchemaChange()));
            registeredModels.add(models.add(
                AIAgentSessionDataRecord.class, DefaultScopeDefine.AI_AGENT_SESSION_DATA,
                new Storage(AIAgentSessionDataRecord.INDEX_NAME, true, DownSampling.Second),
                StorageManipulationOpt.withSchemaChange()));
        }
        registeredModels.forEach(model -> MetadataRegistry.INSTANCE.registerStreamModel(model, config));
        final BanyanDBStorageClient client = mock(BanyanDBStorageClient.class);
        when(client.getResultWindowMaxSize()).thenReturn(10000);
        final StreamQueryResponse response = mock(StreamQueryResponse.class);
        when(response.getElements()).thenReturn(Collections.emptyList());
        when(client.queryStream(anyInt(), anyString(), any(Serializable[].class))).thenAnswer(invocation -> {
            emitted.add(invocation.getArgument(1));
            return response;
        });
        dao = new BanyanDBAIAgentConversationQueryDAO(client);
    }

    @AfterEach
    void tearDown() {
        registeredModels.forEach(MetadataRegistry.INSTANCE::evict);
    }

    @Test
    void roundsWithoutDurationDoNotQueryColdStorageEvenWhenEnabled() throws IOException {
        dao.queryRoundsDebuggable("service", null, "conversation", null, 100, true);

        assertEquals(1, emitted.size());
        assertFalse(emitted.get(0).contains(" ON "));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eachReadQueriesOnlyTheRequestedStages(final boolean coldStage) throws IOException {
        final Duration duration = mock(Duration.class);
        when(duration.isColdStage()).thenReturn(coldStage);
        when(duration.getStartTimestamp()).thenReturn(1000L);
        when(duration.getEndTimestamp()).thenReturn(2000L);

        dao.queryRoundsDebuggable("service", null, null, duration, 100, false);
        dao.queryHeadRoundDebuggable("service", null, "conversation", coldStage);
        dao.queryRoundsByNumberDebuggable("service", null, "conversation", 1, 5, 1024, coldStage);
        dao.queryFilesDebuggable("service", null, "session", 1000, 2000, 1, 5, 1024, coldStage);

        assertEquals(4, emitted.size(), "each read must issue exactly one storage query");
        for (final String query : emitted) {
            assertTrue(query.contains("recordsAIAgent"), query);
            assertEquals(coldStage, query.contains(" ON cold STAGES"), query);
            if (!coldStage) {
                assertFalse(query.contains(" ON "), query);
            }
        }
    }
}
