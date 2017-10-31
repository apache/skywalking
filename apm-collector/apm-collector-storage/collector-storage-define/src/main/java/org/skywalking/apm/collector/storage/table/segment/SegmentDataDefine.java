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

package org.skywalking.apm.collector.storage.table.segment;

import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.operator.CoverOperation;
import org.skywalking.apm.collector.storage.define.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class SegmentDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 2;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(SegmentTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(SegmentTable.COLUMN_DATA_BINARY, AttributeType.BYTE, new CoverOperation()));
    }
}
