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
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.storage.model.Model;

/**
 * A physical table and how its dated indices are rolled. The physical name is also the alias every dated
 * index of the table carries, see {@link StorageEsInstaller}.
 */
@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class IndexFamily {
    /**
     * The merged metrics index a foreign metric of the inspect API lives in. It is used where the read names
     * the index itself instead of resolving the metric through the inspect overlay.
     */
    public static final IndexFamily FOREIGN_METRICS =
        new IndexFamily(IndexController.METRICS_LOGIC_TABLE_NAME, IndexRolling.DAY_STEP);

    private final String physicalName;
    private final IndexRolling rolling;

    public static IndexFamily of(final Model model) {
        return new IndexFamily(IndexController.INSTANCE.getTableName(model), IndexRolling.of(model));
    }

    /**
     * Resolves a logical name the way {@link IndexController.LogicIndicesRegister#getPhysicalTableName} does.
     * A table no model registered is rolled by {@code dayStep}: only metric reads pass names that may be
     * unregistered (a foreign metric, a metric added by a runtime rule), and metrics are rolled by
     * {@code dayStep}.
     */
    public static IndexFamily of(final String logicName) {
        final String physicalName = IndexController.LogicIndicesRegister.getPhysicalTableName(logicName);
        final IndexRolling registered = IndexController.LogicIndicesRegister.getRolling(physicalName);
        return new IndexFamily(physicalName, registered != null ? registered : IndexRolling.DAY_STEP);
    }
}
