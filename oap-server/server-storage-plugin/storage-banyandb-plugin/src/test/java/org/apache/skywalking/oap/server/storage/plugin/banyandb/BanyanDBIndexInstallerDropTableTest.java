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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import io.grpc.Status;
import java.time.Duration;
import java.util.Arrays;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.schema.v1.BanyandbSchema.SchemaKey;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.SchemaWatcher;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dropping a measure drops the TopN aggregations built on it before the measure, and only those: one that fails to
 * drop stops the drop before the measure goes, so the runtime-rule removal fails and tries again with the model
 * still registered, instead of leaving the aggregation on a dropped measure.
 */
public class BanyanDBIndexInstallerDropTableTest {
    private static final String RULE = "service_relation_server_side-service";
    private static final String OTHER_RULE = "service_relation_client_side-service";

    private BanyanDBClient banyanDB;
    private BanyanDBIndexInstaller installer;
    private Model model;
    private String group;
    private String measure;

    @BeforeEach
    public void mockBanyanDB() throws Exception {
        banyanDB = mock(BanyanDBClient.class);
        final BanyanDBStorageClient client = mock(BanyanDBStorageClient.class);
        ReflectUtil.setInternalState(client, "client", banyanDB);
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder services = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(core);
        when(core.provider()).thenReturn(services);
        final DownSamplingConfigService downSampling = new DownSamplingConfigService(Arrays.asList("Hour", "Day"));
        when(services.getService(DownSamplingConfigService.class)).thenReturn(downSampling);
        final BanyanDBStorageConfig config = new BanyanDBStorageConfig();
        installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        try (MockedStatic<DefaultScopeDefine> ignored = mockStatic(DefaultScopeDefine.class)) {
            model = new StorageModels().add(
                ServiceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
                new Storage(ServiceRelationServerSideMetrics.INDEX_NAME, false, DownSampling.Minute),
                StorageManipulationOpt.runtimeApply());
        }
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(model, config, downSampling);
        group = metadata.getGroup();
        measure = metadata.name();
        when(banyanDB.findTopNAggregations(group)).thenReturn(Arrays.asList(
            topN(OTHER_RULE, "service_relation_client_side_minute"), topN(RULE, measure)));
        final SchemaWatcher watcher = mock(SchemaWatcher.class);
        when(watcher.awaitSchemaDeleted(any(SchemaKey.class), any(Duration.class))).thenReturn(SchemaWatcher.Result.applied());
        when(banyanDB.getSchemaWatcher()).thenReturn(watcher);
    }

    @Test
    public void keepsTheMeasureWhenItsTopNFailsToDrop() throws Exception {
        when(banyanDB.deleteTopNAggregationWithRevision(group, RULE))
            .thenThrow(new BanyanDBException("unavailable", null, Status.Code.UNAVAILABLE, true));

        assertThrows(StorageException.class, () -> installer.dropTable(model));

        verify(banyanDB, never()).deleteIndexRuleBindingWithRevision(anyString(), anyString());
        verify(banyanDB, never()).deleteMeasureWithRevision(anyString(), anyString());
        verify(banyanDB, never()).deleteTopNAggregationWithRevision(group, OTHER_RULE);
    }

    @Test
    public void dropsTheMeasureWhenItsTopNIsAlreadyGone() throws Exception {
        when(banyanDB.deleteTopNAggregationWithRevision(group, RULE))
            .thenThrow(new BanyanDBException("not found", null, Status.Code.NOT_FOUND, false));

        installer.dropTable(model);

        verify(banyanDB).deleteIndexRuleBindingWithRevision(group, measure);
        verify(banyanDB).deleteMeasureWithRevision(group, measure);
        verify(banyanDB, never()).deleteTopNAggregationWithRevision(group, OTHER_RULE);
    }

    private static TopNAggregation topN(String name, String sourceMeasure) {
        return TopNAggregation.newBuilder()
                              .setMetadata(BanyandbCommon.Metadata.newBuilder().setName(name))
                              .setSourceMeasure(BanyandbCommon.Metadata.newBuilder().setName(sourceMeasure))
                              .build();
    }
}
