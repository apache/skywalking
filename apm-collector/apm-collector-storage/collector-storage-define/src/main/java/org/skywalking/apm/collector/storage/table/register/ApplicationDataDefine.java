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

package org.skywalking.apm.collector.storage.table.register;

import org.skywalking.apm.collector.core.data.Attribute;
import org.skywalking.apm.collector.core.data.AttributeType;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.DataDefine;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;
import org.skywalking.apm.collector.remote.RemoteDataMapping;

/**
 * @author peng-yongsheng
 */
public class ApplicationDataDefine extends DataDefine {

    @Override public int remoteDataMappingId() {
        return RemoteDataMapping.Application.ordinal();
    }

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ApplicationTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ApplicationTable.COLUMN_APPLICATION_CODE, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(ApplicationTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
    }

    public enum Application {
        INSTANCE;

        public String getId(Data data) {
            return data.getDataString(0);
        }

        public String getApplicationCode(Data data) {
            return data.getDataString(1);
        }

        public int getApplicationId(Data data) {
            return data.getDataInteger(1);
        }
    }
}
