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

package org.skywalking.apm.collector.storage.table.noderef;

import org.skywalking.apm.collector.core.data.Attribute;
import org.skywalking.apm.collector.core.data.AttributeType;
import org.skywalking.apm.collector.core.data.DataDefine;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;
import org.skywalking.apm.collector.remote.RemoteDataMapping;

/**
 * @author peng-yongsheng
 */
public class NodeReferenceDataDefine extends DataDefine {

    @Override public int remoteDataMappingId() {
        return RemoteDataMapping.NodeReference.ordinal();
    }

    @Override protected int initialCapacity() {
        return 11;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(NodeReferenceTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(2, new Attribute(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, AttributeType.INTEGER, new NonOperation()));
        addAttribute(3, new Attribute(NodeReferenceTable.COLUMN_BEHIND_PEER, AttributeType.STRING, new NonOperation()));
        addAttribute(4, new Attribute(NodeReferenceTable.COLUMN_S1_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(5, new Attribute(NodeReferenceTable.COLUMN_S3_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(6, new Attribute(NodeReferenceTable.COLUMN_S5_LTE, AttributeType.INTEGER, new AddOperation()));
        addAttribute(7, new Attribute(NodeReferenceTable.COLUMN_S5_GT, AttributeType.INTEGER, new AddOperation()));
        addAttribute(8, new Attribute(NodeReferenceTable.COLUMN_SUMMARY, AttributeType.INTEGER, new AddOperation()));
        addAttribute(9, new Attribute(NodeReferenceTable.COLUMN_ERROR, AttributeType.INTEGER, new AddOperation()));
        addAttribute(10, new Attribute(NodeReferenceTable.COLUMN_TIME_BUCKET, AttributeType.LONG, new NonOperation()));
    }
}
