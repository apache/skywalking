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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.lang.reflect.Type;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchColumnTypeMappingTestCase {
    public List<String> a;

    @Test
    public void test() throws NoSuchFieldException {
        ColumnTypeEsMapping mapping = new ColumnTypeEsMapping();

        Assert.assertEquals("integer", mapping.transform(int.class, int.class));
        Assert.assertEquals("integer", mapping.transform(Integer.class, Integer.class));

        Assert.assertEquals("long", mapping.transform(long.class, long.class));
        Assert.assertEquals("long", mapping.transform(Long.class, Long.class));

        Assert.assertEquals("double", mapping.transform(double.class, double.class));
        Assert.assertEquals("double", mapping.transform(Double.class, Double.class));

        Assert.assertEquals("keyword", mapping.transform(String.class, String.class));

        final Type listFieldType = this.getClass().getField("a").getGenericType();
        Assert.assertEquals("keyword", mapping.transform(List.class, listFieldType));
    }
}
