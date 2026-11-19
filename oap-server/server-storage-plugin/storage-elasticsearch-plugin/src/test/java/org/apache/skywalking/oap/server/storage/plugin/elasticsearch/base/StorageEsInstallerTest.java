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

import java.util.Collections;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.ElasticSearchModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StorageEsInstallerTest {

    @Mock
    private ElasticSearchClient client;

    @Mock
    private ModuleManager moduleManager;

    @Test
    void whenCreating_withoutSchemaChange_registersThePhysicalTableWithoutCallingElasticsearch() throws Exception {
        final String metric = "installer_test_metric";
        final Model model = new Model(
            metric, Collections.emptyList(), 0, DownSampling.Minute, false, Metrics.class, true, false,
            new SQLDatabaseModelExtension(), new BanyanDBModelExtension(), new ElasticSearchModelExtension()
        );

        new StorageEsInstaller(client, moduleManager, new StorageModuleElasticsearchConfig())
            .whenCreating(model, StorageManipulationOpt.withoutSchemaChange());

        assertEquals(
            IndexController.METRICS_LOGIC_TABLE_NAME,
            IndexController.LogicIndicesRegister.getPhysicalTableName(metric)
        );
        verifyNoInteractions(client);
    }

    /**
     * A legacy template (Elasticsearch before 7.8) keeps a negative order, so one stored with 0 is updated to the
     * configured -1. A composable template keeps a negative order as priority 0, which is then up to date.
     */
    @Test
    void comparesALegacyTemplateOrderAsConfiguredAndAComposablePriorityAtLeastZero() {
        assertTrue(StorageEsInstaller.hasConfiguredOrder(template(-1, false), -1));
        assertFalse(StorageEsInstaller.hasConfiguredOrder(template(0, false), -1));
        assertFalse(StorageEsInstaller.hasConfiguredOrder(template(0, false), 3));
        assertTrue(StorageEsInstaller.hasConfiguredOrder(template(3, false), 3));

        assertTrue(StorageEsInstaller.hasConfiguredOrder(template(0, true), -1));
        assertTrue(StorageEsInstaller.hasConfiguredOrder(template(0, true), 0));
        assertFalse(StorageEsInstaller.hasConfiguredOrder(template(5, true), 0));
        assertTrue(StorageEsInstaller.hasConfiguredOrder(template(5, true), 5));
    }

    private static IndexTemplate template(final long order, final boolean composable) {
        final IndexTemplate template = new IndexTemplate();
        template.setOrder(order);
        template.setComposable(composable);
        return template;
    }
}
