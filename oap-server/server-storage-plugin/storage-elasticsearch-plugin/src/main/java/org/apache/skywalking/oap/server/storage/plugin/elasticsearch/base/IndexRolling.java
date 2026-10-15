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

import org.apache.skywalking.oap.server.core.storage.model.Model;

/**
 * How the dated indices of one physical table are rolled. Every model sharing a physical table has the same
 * rolling, see {@link IndexController.LogicIndicesRegister#registerRelation}.
 */
public enum IndexRolling {
    /**
     * Management data: one concrete index, no date suffix.
     */
    NONE,
    /**
     * Metrics of every downsampling, and records that are not super datasets, rolled by {@code dayStep}.
     */
    DAY_STEP,
    /**
     * Super dataset records (traces, logs, spans), rolled by {@code superDatasetDayStep}.
     */
    SUPER_DATASET_DAY_STEP;

    public static IndexRolling of(final Model model) {
        if (!model.isTimeSeries()) {
            return NONE;
        }
        if (model.isRecord() && model.isSuperDataset()) {
            return SUPER_DATASET_DAY_STEP;
        }
        return DAY_STEP;
    }
}
