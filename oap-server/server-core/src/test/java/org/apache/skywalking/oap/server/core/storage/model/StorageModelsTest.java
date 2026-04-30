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
    public void rolledBackOnListenerFailure() throws StorageException {
        // A CreatingListener throw must NOT leave the model in `models`. Future retries
        // would otherwise hit the dedup short-circuit and skip listeners entirely, leaving
        // the storage stack permanently half-built.
        StorageModels models = new StorageModels();
        models.addModelListener((model, opt) -> {
            throw new StorageException("simulated DDL failure");
        });
        Assertions.assertThrows(StorageException.class, () -> models.add(TestModel.class, -1,
            new Storage("StorageModelsRollbackTest", false, DownSampling.Hour),
            StorageManipulationOpt.withSchemaChange()));
        // Registry must not retain the model — a retry would otherwise dedup-skip the
        // listener instead of attempting the DDL again.
        assertEquals(0, models.allModels().size());
    }

    @Test
    public void removeKeepsModelOnListenerFailure() throws StorageException {
        // remove() must keep the model in `models` if any whenRemoving listener throws —
        // otherwise the registry diverges from the backend (model gone, BanyanDB measure
        // still alive) and there's nothing for the retry path to find. Listeners are
        // required to be idempotent on the drop, so re-firing them on retry is safe.
        StorageModels models = new StorageModels();
        models.add(TestModel.class, -1,
            new Storage("StorageModelsRemoveRetryTest", false, DownSampling.Hour),
            StorageManipulationOpt.withSchemaChange());
        assertEquals(1, models.allModels().size());

        // Listener that throws on remove (simulating BanyanDB delete-measure transient failure).
        // Note: addModelListener fires whenCreating for already-added models, but our listener
        // only overrides whenRemoving, so the catch-up call is a no-op via the default impl.
        models.addModelListener(new ModelRegistry.CreatingListener() {
            @Override
            public void whenCreating(final Model model, final StorageManipulationOpt opt) {
                // already-created catch-up — fine to no-op for this test
            }

            @Override
            public void whenRemoving(final Model model, final StorageManipulationOpt opt) throws StorageException {
                throw new StorageException("simulated dropTable failure");
            }
        });

        Assertions.assertThrows(StorageException.class,
            () -> models.remove(TestModel.class, StorageManipulationOpt.withSchemaChange()));
        // Model must still be in the registry — the next retry needs to find and drive
        // dropTable again. Otherwise the operator's /inactivate succeeds locally but the
        // backend measure stays orphaned forever.
        assertEquals(1, models.allModels().size());
    }

    @Test
    public void testStorageModels() throws StorageException {
        StorageModels models = new StorageModels();
        models.add(TestModel.class, -1,
                   new Storage("StorageModelsTest", false, DownSampling.Hour),
                   StorageManipulationOpt.withSchemaChange()
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
        @SQLDatabase.CompositeIndex(withColumns = {"column2"})
        private String column1;

        @Column(name = "column2")
        @SQLDatabase.CompositeIndex(withColumns = {"column1"})
        @SQLDatabase.CompositeIndex(withColumns = {"column"})
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
