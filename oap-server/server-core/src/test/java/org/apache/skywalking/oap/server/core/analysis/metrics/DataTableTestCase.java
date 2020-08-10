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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DataTableTestCase {

    private DataTable dataTable;

    @Before
    public void init() {
        dataTable = new DataTable();
        dataTable.valueAccumulation("5", 500L);
        dataTable.valueAccumulation("6", 600L);
        dataTable.valueAccumulation("1", 100L);
        dataTable.valueAccumulation("2", 200L);
        dataTable.valueAccumulation("7", 700L);
    }

    @Test
    public void toStorageData() {
        Assert.assertEquals("1,100|2,200|5,500|6,600|7,700", dataTable.toStorageData());
    }

    @Test
    public void toObject() {
        DataTable dataTable = new DataTable();
        dataTable.toObject("1,100|2,200|5,500|6,600|7,700");

        Assert.assertEquals(100, dataTable.get("1").intValue());
        Assert.assertEquals(200, dataTable.get("2").intValue());
        Assert.assertEquals(500, dataTable.get("5").intValue());
        Assert.assertEquals(600, dataTable.get("6").intValue());
        Assert.assertEquals(700, dataTable.get("7").intValue());
    }

    @Test
    public void copyFrom() {
        DataTable dataTable = new DataTable();
        dataTable.append(this.dataTable);

        Assert.assertEquals("1,100|2,200|5,500|6,600|7,700", dataTable.toStorageData());
    }
}
