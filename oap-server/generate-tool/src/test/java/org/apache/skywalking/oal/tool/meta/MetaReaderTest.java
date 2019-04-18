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

package org.apache.skywalking.oal.tool.meta;

import java.io.InputStream;
import java.util.List;
import org.apache.skywalking.oal.tool.parser.SourceColumn;
import org.junit.*;

public class MetaReaderTest {
    @Test
    public void testFileParser() {
        MetaReader reader = new MetaReader();
        InputStream stream = MetaReaderTest.class.getResourceAsStream("/scope-meta.yml");
        MetaSettings metaSettings = reader.read(stream);
        Assert.assertNotEquals(0, metaSettings.getScopes().size());

        metaSettings.getScopes().forEach(scopeMeta -> {
            List<SourceColumn> sourceColumns = MockSourceColumnsFactory.getColumns(scopeMeta.getName());
            for (int i = 0; i < sourceColumns.size(); i++) {
                SourceColumn column = scopeMeta.getColumns().get(i);
                SourceColumn expected = sourceColumns.get(i);
                Assert.assertEquals(expected, column);
            }
        });
    }
}
