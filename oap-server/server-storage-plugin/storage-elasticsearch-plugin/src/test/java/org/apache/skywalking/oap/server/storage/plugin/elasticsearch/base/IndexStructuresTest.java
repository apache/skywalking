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

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class IndexStructuresTest {

    @Test
    public void getMapping() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure("test", structures.getWrapper().wrapper(properties));
        Map<String, Object> mapping = structures.getMapping("test");
        Assert.assertEquals(structures.getExtractor().extract(mapping), properties);

        structures.putStructure("test2", structures.getWrapper().wrapper(new HashMap<>()));
        mapping = structures.getMapping("test2");

        Assert.assertTrue(structures.getExtractor().extract(mapping).isEmpty());
    }

    @Test
    public void resolveStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure("test", structures.getWrapper().wrapper(properties));
        Map<String, Object> mapping = structures.getMapping("test");
        Assert.assertEquals(properties, structures.getExtractor().extract(mapping));
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("f", "g");
        structures.putStructure("test", structures.getWrapper().wrapper(properties2));
        mapping = structures.getMapping("test");
        HashMap<String, Object> res = new HashMap<>();
        res.put("a", "b");
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, structures.getExtractor().extract(mapping));
    }

    @Test
    public void diffStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure("test", structures.getWrapper().wrapper(properties));
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        Map<String, Object> diffMappings = structures.diffStructure(
            "test", structures.getWrapper().wrapper(properties2));
        HashMap<String, Object> res = new HashMap<>();
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, structures.getExtractor().extract(diffMappings));
        diffMappings = structures.diffStructure("test", structures.getWrapper().wrapper(properties));
        Assert.assertEquals(new HashMap<>(), structures.getExtractor().extract(diffMappings));
    }

    @Test
    public void containsStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure("test", structures.getWrapper().wrapper(properties));

        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("c", "d");
        Assert.assertTrue(structures.containsStructure("test", structures.getWrapper().wrapper(properties2)));

        HashMap<String, Object> properties3 = new HashMap<>();
        properties3.put("a", "b");
        properties3.put("q", "d");
        Assert.assertFalse(structures.containsStructure("test", structures.getWrapper().wrapper(properties3)));
    }
}