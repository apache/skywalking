/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.table.jvm;

import org.skywalking.apm.collector.core.data.Attribute;
import org.skywalking.apm.collector.core.data.AttributeType;
import org.skywalking.apm.collector.core.data.DataDefine;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;
import org.skywalking.apm.collector.remote.RemoteDataMapping;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricDataDefine extends DataDefine {

    @Override public int remoteDataMappingId() {
        return RemoteDataMapping.MemoryPoolMetric.ordinal();
    }

    @Override protected int initialCapacity() {
        return 8;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(MemoryPoolMetricTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(MemoryPoolMetricTable.COLUMN_POOL_TYPE, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(MemoryPoolMetricTable.COLUMN_INIT, AttributeType.LONG, new CoverOperation()));
        addAttribute(4, new Attribute(MemoryPoolMetricTable.COLUMN_MAX, AttributeType.LONG, new CoverOperation()));
        addAttribute(5, new Attribute(MemoryPoolMetricTable.COLUMN_USED, AttributeType.LONG, new CoverOperation()));
        addAttribute(6, new Attribute(MemoryPoolMetricTable.COLUMN_COMMITTED, AttributeType.LONG, new CoverOperation()));
        addAttribute(7, new Attribute(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new CoverOperation()));
    }
}
