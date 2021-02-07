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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.QueryUnifiedIndex;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DefaultScopeDefine.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class StorageModelsTest {
    @BeforeClass
    public static void setup() {
        PowerMockito.mockStatic(DefaultScopeDefine.class);
        PowerMockito.when(DefaultScopeDefine.nameOf(-1)).thenReturn("any");
    }

    @Test
    public void testStorageModels() throws StorageException {
        StorageModels models = new StorageModels();
        models.add(TestModel.class, -1,
                   new Storage("StorageModelsTest", DownSampling.Hour),
                   false
        );

        final List<Model> allModules = models.allModels();
        Assert.assertEquals(1, allModules.size());

        final Model model = allModules.get(0);
        Assert.assertEquals(4, model.getColumns().size());
        Assert.assertEquals(false, model.getColumns().get(0).isStorageOnly());
        Assert.assertEquals(false, model.getColumns().get(1).isStorageOnly());
        Assert.assertEquals(false, model.getColumns().get(2).isStorageOnly());
        Assert.assertEquals(true, model.getColumns().get(3).isStorageOnly());

        final List<ExtraQueryIndex> extraQueryIndices = model.getExtraQueryIndices();
        Assert.assertEquals(3, extraQueryIndices.size());
        Assert.assertArrayEquals(new String[] {
            "column2",
            "column"
        }, extraQueryIndices.get(2).getColumns());
    }

    @Stream(name = "StorageModelsTest", scopeId = -1, builder = TestModel.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestModel {
        @Column(columnName = "column")
        private String column;

        @Column(columnName = "column1")
        @QueryUnifiedIndex(withColumns = {"column2"})
        private String column1;

        @Column(columnName = "column2")
        @QueryUnifiedIndex(withColumns = {"column1"})
        @QueryUnifiedIndex(withColumns = {"column"})
        private String column2;

        @Column(columnName = "column", storageOnly = true)
        private String column4;

        static class Builder implements StorageHashMapBuilder<StorageData> {

            @Override
            public StorageData storage2Entity(final Map dbMap) {
                return null;
            }

            @Override
            public Map<String, Object> entity2Storage(final StorageData storageData) {
                return null;
            }
        }
    }
}
