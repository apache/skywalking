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

import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
public class StorageModelsTest {

    private static MockedStatic<DefaultScopeDefine> DEFAULT_SCOPE_DEFINE_MOCKED_STATIC;

    @BeforeAll
    public static void setup() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC = mockStatic(DefaultScopeDefine.class);
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.when(() -> DefaultScopeDefine.nameOf(-1)).thenReturn("any");
    }

    @AfterAll
    public static void tearDown() {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.close();
    }

    @Test
    public void testStorageModels() throws StorageException {
        StorageModels models = new StorageModels();
        models.add(TestModel.class, -1,
                   new Storage("StorageModelsTest", false, DownSampling.Hour),
                   false
        );

        final List<Model> allModules = models.allModels();
        assertEquals(1, allModules.size());

        final Model model = allModules.get(0);
        assertEquals(4, model.getColumns().size());
        assertFalse(model.getColumns().get(0).isStorageOnly());
        assertFalse(model.getColumns().get(1).isStorageOnly());
        assertFalse(model.getColumns().get(2).isStorageOnly());
        Assertions.assertTrue(model.getColumns().get(3).isStorageOnly());

        assertArrayEquals(new String[] {
            "column2",
            "column"
        }, model.getColumns().get(2).getSqlDatabaseExtension().getIndices().get(1).getColumns());
    }

    @Stream(name = "StorageModelsTest", scopeId = -1, builder = TestModel.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestModel {
        @Column(name = "column")
        private String column;

        @Column(name = "column1")
        @SQLDatabase.QueryUnifiedIndex(withColumns = {"column2"})
        private String column1;

        @Column(name = "column2")
        @SQLDatabase.QueryUnifiedIndex(withColumns = {"column1"})
        @SQLDatabase.QueryUnifiedIndex(withColumns = {"column"})
        private String column2;

        @Column(name = "column", storageOnly = true)
        private String column4;

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
