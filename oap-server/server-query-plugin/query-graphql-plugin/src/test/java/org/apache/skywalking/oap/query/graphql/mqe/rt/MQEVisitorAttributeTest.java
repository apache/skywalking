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

package org.apache.skywalking.oap.query.graphql.mqe.rt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.AttrCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the top_n attribute pre-check in {@link MQEVisitor#checkAttributes}: an attribute condition whose
 * key is not a queryable column of the metric must be rejected with a descriptive {@link
 * IllegalExpressionException} before the request reaches storage, instead of letting the backend surface a
 * raw IO exception. Metrics with no attribute columns (relations, database/cache/mq access) are the case
 * that used to fail.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MQEVisitorAttributeTest {
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private IModelManager modelManager;

    private MQEVisitor visitor;

    @BeforeEach
    public void setup() {
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IModelManager.class)).thenReturn(modelManager);
        // Build the model mocks before stubbing allModels(); nesting when() calls confuses Mockito.
        final List<Model> models = Arrays.asList(
            // endpoint_cpm is decorated, so its measure carries the attr0 tag.
            modelWithColumns("endpoint_cpm", "entity_id", "service_id", "attr0"),
            // a relation metric carries no attribute columns at all.
            modelWithColumns("service_relation_client_cpm", "entity_id", "source_service_id", "dest_service_id"));
        when(modelManager.allModels()).thenReturn(models);

        final Duration duration = mock(Duration.class);
        when(duration.getStep()).thenReturn(Step.MINUTE);
        visitor = new MQEVisitor(moduleManager, mock(Entity.class), duration);
    }

    @Test
    public void rejectsAttributeMissingFromMetricSchema() {
        final IllegalExpressionException ex = assertThrows(IllegalExpressionException.class, () ->
            visitor.checkAttributes(
                "service_relation_client_cpm",
                Collections.singletonList(new AttrCondition("attr0", "VIRTUAL_DATABASE", true))));
        assertTrue(ex.getMessage().contains("attr0"));
        assertTrue(ex.getMessage().contains("service_relation_client_cpm"));
    }

    @Test
    public void rejectsNotEqualsAttributeMissingFromMetricSchema() {
        // the same holds for `attr0 != x`, since the check is on the attribute key.
        assertThrows(IllegalExpressionException.class, () ->
            visitor.checkAttributes(
                "service_relation_client_cpm",
                Collections.singletonList(new AttrCondition("attr0", "MESH", false))));
    }

    @Test
    public void allowsAttributePresentInMetricSchema() {
        assertDoesNotThrow(() ->
            visitor.checkAttributes(
                "endpoint_cpm",
                Collections.singletonList(new AttrCondition("attr0", "GENERAL", true))));
    }

    @Test
    public void allowsEmptyAttributes() {
        assertDoesNotThrow(() -> visitor.checkAttributes("service_relation_client_cpm", Collections.emptyList()));
    }

    private static Model modelWithColumns(String name, String... columnNames) {
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn(name);
        final List<ModelColumn> columns = new ArrayList<>(columnNames.length);
        for (final String columnName : columnNames) {
            final ColumnName wrappedName = mock(ColumnName.class);
            when(wrappedName.getName()).thenReturn(columnName);
            final ModelColumn column = mock(ModelColumn.class);
            when(column.getColumnName()).thenReturn(wrappedName);
            when(column.shouldIndex()).thenReturn(true);
            columns.add(column);
        }
        when(model.getColumns()).thenReturn(columns);
        return model;
    }
}
