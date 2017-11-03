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

package org.skywalking.apm.collector.storage.table.serviceref;

import org.skywalking.apm.collector.core.data.Attribute;
import org.skywalking.apm.collector.core.data.AttributeType;
import org.skywalking.apm.collector.core.data.DataDefine;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;
import org.skywalking.apm.collector.remote.RemoteDataMapping;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceDataDefine extends DataDefine {

    @Override public int remoteDataMappingId() {
        return RemoteDataMapping.ServiceReference.ordinal();
    }

    @Override protected int initialCapacity() {
        return 15;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceReferenceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(3, new Attribute(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(4, new Attribute(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(5, new Attribute(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(6, new Attribute(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, AttributeType.STRING, new NonOperation()));
        addAttribute(7, new Attribute(ServiceReferenceTable.COLUMN_S1_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(8, new Attribute(ServiceReferenceTable.COLUMN_S3_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(9, new Attribute(ServiceReferenceTable.COLUMN_S5_LTE, AttributeType.LONG, new AddOperation()));
        addAttribute(10, new Attribute(ServiceReferenceTable.COLUMN_S5_GT, AttributeType.LONG, new AddOperation()));
        addAttribute(11, new Attribute(ServiceReferenceTable.COLUMN_SUMMARY, AttributeType.LONG, new AddOperation()));
        addAttribute(12, new Attribute(ServiceReferenceTable.COLUMN_ERROR, AttributeType.LONG, new AddOperation()));
        addAttribute(13, new Attribute(ServiceReferenceTable.COLUMN_COST_SUMMARY, AttributeType.LONG, new AddOperation()));
        addAttribute(14, new Attribute(ServiceReferenceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }
}
