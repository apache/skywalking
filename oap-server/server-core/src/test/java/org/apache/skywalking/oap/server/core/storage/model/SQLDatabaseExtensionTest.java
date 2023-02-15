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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SQLDatabaseExtensionTest {
    @Test
    public void testIndexColumns() {
        final SQLDatabaseExtension.MultiColumnsIndex extraQueryIndex = new SQLDatabaseExtension.MultiColumnsIndex(
                "a1", new String[]{"a2"});
        Assertions.assertArrayEquals(new String[]{
                "a1",
                "a2"
        }, extraQueryIndex.getColumns());
    }

    @Test
    public void testIllegalIndexColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> new SQLDatabaseExtension.MultiColumnsIndex("a1", new String[0]));
    }
}
