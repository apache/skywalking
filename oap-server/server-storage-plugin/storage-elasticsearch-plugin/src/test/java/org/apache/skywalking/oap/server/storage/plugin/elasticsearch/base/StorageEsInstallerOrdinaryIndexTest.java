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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Boot against the existing ordinary index ({@code management}, which holds every non-time-series model): it appends
 * the fields the index lacks. The index keeps its settings, which only a new index takes (a WARN, as before).
 */
@ExtendWith(MockitoExtension.class)
class StorageEsInstallerOrdinaryIndexTest {
    private static final String INDEX = "management";

    private static MockedStatic<DefaultScopeDefine> DEFAULT_SCOPE_DEFINE_MOCKED_STATIC;

    @Mock
    private ElasticSearchClient client;

    @Mock
    private ModuleManager moduleManager;

    @BeforeAll
    static void setup() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC = mockStatic(DefaultScopeDefine.class);
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.when(() -> DefaultScopeDefine.nameOf(-1)).thenReturn("any");
    }

    @AfterAll
    static void tearDown() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.close();
    }

    @Test
    void appendsTheMissingField() throws Exception {
        final StorageEsInstaller installer = new StorageEsInstaller(
            client, moduleManager, new StorageModuleElasticsearchConfig());
        final Model model = model();
        final Map<String, Object> liveProperties = new HashMap<>(installer.createMapping(model).getProperties());
        liveProperties.remove("b");
        stubLiveIndex(liveProperties, indexSettings(installer, model));

        final StorageManipulationOpt opt = StorageManipulationOpt.bootInstall();
        installer.whenCreating(model, opt);

        verify(client).updateIndexMapping(eq(INDEX), argThat(m -> m.getProperties().keySet().equals(Set.of("b"))));
        assertOnlyTheAddedField(opt);
    }

    @Test
    void appendsTheMissingFieldAndKeepsTheIndexSettings() throws Exception {
        final StorageEsInstaller installer = new StorageEsInstaller(
            client, moduleManager, new StorageModuleElasticsearchConfig());
        final Model model = model();
        final Map<String, Object> liveProperties = new HashMap<>(installer.createMapping(model).getProperties());
        liveProperties.remove("b");
        final Map<String, Object> liveIndexSettings = new HashMap<>(indexSettings(installer, model));
        liveIndexSettings.put("number_of_shards", "5");
        stubLiveIndex(liveProperties, liveIndexSettings);

        final StorageManipulationOpt opt = StorageManipulationOpt.bootInstall();
        installer.whenCreating(model, opt);

        verify(client).updateIndexMapping(eq(INDEX), argThat(m -> m.getProperties().keySet().equals(Set.of("b"))));
        assertOnlyTheAddedField(opt);
    }

    private static void assertOnlyTheAddedField(final StorageManipulationOpt opt) {
        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        assertEquals(1, outcomes.size(), outcomes::toString);
        assertEquals("index", outcomes.get(0).getResourceType());
        assertEquals(INDEX, outcomes.get(0).getResourceName());
        assertEquals(StorageManipulationOpt.Outcome.UPDATED, outcomes.get(0).getStatus());
        assertEquals("adds fields [b]", outcomes.get(0).getDiff());
    }

    /**
     * Each read returns a fresh copy, as a real response does: the installer's structures keep the maps they read.
     */
    private void stubLiveIndex(final Map<String, Object> properties,
                               final Map<String, Object> indexSettings) {
        when(client.isExistsIndex(INDEX)).thenReturn(true);
        when(client.getIndex(INDEX)).thenAnswer(i -> {
            final Mappings mappings = new Mappings();
            mappings.setProperties(new HashMap<>(properties));
            final Index live = new Index();
            live.setMappings(mappings);
            live.setSettings(Map.of("index", new HashMap<>(indexSettings)));
            return Optional.of(live);
        });
        when(client.updateIndexMapping(eq(INDEX), any())).thenReturn(true);
        when(client.formatIndexName(INDEX)).thenReturn(INDEX);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> indexSettings(final StorageEsInstaller installer,
                                                     final Model model) throws Exception {
        return (Map<String, Object>) installer.createSetting(model).get("index");
    }

    private static Model model() throws Exception {
        return new StorageModels().add(TestManagementModel.class, -1,
            new Storage("es_installer_test", false, DownSampling.None), StorageManipulationOpt.bootInstall());
    }

    @Stream(name = "es_installer_test", scopeId = -1, builder = TestManagementModel.Builder.class,
        processor = ManagementStreamProcessor.class)
    private static class TestManagementModel {
        @Column(name = "a")
        private String a;

        @Column(name = "b")
        private String b;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }
}
