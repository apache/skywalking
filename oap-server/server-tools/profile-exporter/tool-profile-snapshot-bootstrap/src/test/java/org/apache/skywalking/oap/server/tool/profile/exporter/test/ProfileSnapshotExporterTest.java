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

package org.apache.skywalking.oap.server.tool.profile.exporter.test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.config.ComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.query.ProfileTaskQueryService;
import org.apache.skywalking.oap.server.core.query.TraceQueryService;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.tool.profile.exporter.ExporterConfig;
import org.apache.skywalking.oap.server.tool.profile.exporter.ProfileSnapshotDumper;
import org.apache.skywalking.oap.server.tool.profile.exporter.ProfiledBasicInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.yaml.snakeyaml.Yaml;

@RunWith(PowerMockRunner.class)
public class ProfileSnapshotExporterTest {

    @Mock
    private CoreModuleProvider moduleProvider;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private CoreModuleConfig coreModuleConfig;

    private ExportedData exportedData;

    @Before
    public void init() throws IOException {
        CoreModule coreModule = Mockito.spy(CoreModule.class);
        StorageModule storageModule = Mockito.spy(StorageModule.class);
        Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);
        Whitebox.setInternalState(storageModule, "loadedProvider", moduleProvider);
        Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        Mockito.when(moduleManager.find(StorageModule.NAME)).thenReturn(storageModule);
        final ProfileTaskQueryService taskQueryService = new ProfileTaskQueryService(moduleManager, coreModuleConfig);

        Mockito.when(moduleProvider.getService(IComponentLibraryCatalogService.class))
               .thenReturn(new ComponentLibraryCatalogService());
        Mockito.when(moduleProvider.getService(ProfileTaskQueryService.class)).thenReturn(taskQueryService);
        Mockito.when(moduleProvider.getService(TraceQueryService.class))
               .thenReturn(new TraceQueryService(moduleManager));

        try (final Reader reader = ResourceUtils.read("profile.yml");) {
            exportedData = new Yaml().loadAs(reader, ExportedData.class);
        }
        Mockito.when(moduleProvider.getService(IProfileThreadSnapshotQueryDAO.class))
               .thenReturn(new ProfileExportSnapshotDAO(exportedData));
        Mockito.when(moduleProvider.getService(ITraceQueryDAO.class)).thenReturn(new ProfileTraceDAO(exportedData));
    }

    @Test
    public void test() throws IOException {
        final ExporterConfig config = new ExporterConfig();
        config.setTraceId(exportedData.getTraceId());
        config.setTaskId(exportedData.getTaskId());
        config.setAnalyzeResultDist(new File("").getAbsolutePath());

        // dump
        final ProfiledBasicInfo basicInfo = ProfiledBasicInfo.build(config, moduleManager);
        final File writeFile = ProfileSnapshotDumper.dump(basicInfo, moduleManager);
        Assert.assertTrue(writeFile != null);
        Assert.assertTrue(writeFile.exists());

        // parse
        final ProfileAnalyzeTimeRange timeRange = new ProfileAnalyzeTimeRange();
        timeRange.setStart(exportedData.getSpans().get(0).getStart());
        timeRange.setEnd(exportedData.getSpans().get(0).getEnd());
        final List<ThreadSnapshot> threadSnapshots = ProfileSnapshotDumper.parseFromFileWithTimeRange(
            writeFile, Collections.singletonList(timeRange));

        Assert.assertEquals(threadSnapshots.size(), exportedData.getSnapshots().size());
        for (int i = 0; i < threadSnapshots.size(); i++) {
            Assert.assertEquals(threadSnapshots.get(i).getSequence(), i);
            Assert.assertEquals(threadSnapshots.get(i).getTime(), i * exportedData.getLimit());

            final String[] snapshots = exportedData.getSnapshots().get(i).split("-");
            for (int snapshotIndex = 0; snapshotIndex < snapshots.length; snapshotIndex++) {
                Assert.assertEquals(
                    threadSnapshots.get(i).getStack().getCodeSignaturesList().get(snapshotIndex),
                    snapshots[snapshotIndex]
                );
            }
        }

        writeFile.delete();
    }

}
