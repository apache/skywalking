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

package org.apache.skywalking.oap.server.core.storage.model;

import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.junit.Assert;
import org.junit.Test;

public class ModelColumnTest {
    @Test
    public void testColumnDefine() {
        ModelColumn column = new ModelColumn(new ColumnName("", "abc"), byte[].class, byte[].class, true,
                                             false, true, 0
        );
        Assert.assertEquals(true, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());

        column = new ModelColumn(new ColumnName("", "abc"), DataTable.class, DataTable.class, true,
                                 false, true, 200
        );
        Assert.assertEquals(true, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());
        Assert.assertEquals(200, column.getLength());

        column = new ModelColumn(new ColumnName("", "abc"), String.class, String.class, true,
                                 false, true, 200
        );
        Assert.assertEquals(false, column.isStorageOnly());
        Assert.assertEquals("abc", column.getColumnName().getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConflictDefinition() {
        ModelColumn column = new ModelColumn(new ColumnName("", "abc"), String.class, String.class,
                                             true, true, true, 200
        );
    }
}
