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

package org.skywalking.apm.collector.storage.table.service;

import org.skywalking.apm.collector.storage.base.define.Attribute;
import org.skywalking.apm.collector.storage.base.define.AttributeType;
import org.skywalking.apm.collector.storage.base.define.DataDefine;
import org.skywalking.apm.collector.storage.base.define.operator.CoverOperation;
import org.skywalking.apm.collector.storage.base.define.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 6;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceEntryTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceEntryTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(2, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(4, new Attribute(ServiceEntryTable.COLUMN_REGISTER_TIME, AttributeType.LONG, new NonOperation()));
        addAttribute(5, new Attribute(ServiceEntryTable.COLUMN_NEWEST_TIME, AttributeType.LONG, new CoverOperation()));
    }
}
