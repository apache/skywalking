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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.junit.Assert;
import org.junit.Test;

public class IndexStructuresTest {

    @Test
    public void getMapping() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .build(), new HashMap<>());
        Mappings mapping = structures.getMapping("test");
        Assert.assertEquals(mapping.getProperties(), properties);

        structures.putStructure(
            "test2", Mappings.builder()
                             .type(ElasticSearchClient.TYPE)
                             .properties(new HashMap<>())
                             .build(), new HashMap<>());
        mapping = structures.getMapping("test2");

        Assert.assertTrue(mapping.getProperties().isEmpty());
        //test with source
        IndexStructures structuresSource = new IndexStructures();
        Mappings.Source source = new Mappings.Source();
        source.getExcludes().add("a");
        structuresSource.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .source(source)
                            .build(), new HashMap<>());
        Assert.assertEquals(properties, structuresSource.getMapping("test").getProperties());
        Assert.assertEquals(source.getExcludes(), structuresSource.getMapping("test").getSource().getExcludes());
    }

    @Test
    public void resolveStructure() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .source(new Mappings.Source())
                            .build(), new HashMap<>());
        Mappings mapping = structures.getMapping("test");
        Assert.assertEquals(properties, mapping.getProperties());
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("f", "g");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .source(new Mappings.Source())
                            .build(), new HashMap<>());
        mapping = structures.getMapping("test");
        HashMap<String, Object> res = new HashMap<>();
        res.put("a", "b");
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, mapping.getProperties());

        //test with source
        IndexStructures structuresSource = new IndexStructures();
        Mappings.Source source = new Mappings.Source();
        source.getExcludes().addAll(Arrays.asList("a", "b", "c"));
        structuresSource.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .source(source)
                            .build(), new HashMap<>());
        Assert.assertEquals(properties, structuresSource.getMapping("test").getProperties());
        Assert.assertEquals(source.getExcludes(), structuresSource.getMapping("test").getSource().getExcludes());

        Mappings.Source source2 = new Mappings.Source();
        source.getExcludes().addAll(Arrays.asList("b", "c", "d", "e"));
        structuresSource.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .source(source)
                            .build(), new HashMap<>());
        Assert.assertEquals(res, structuresSource.getMapping("test").getProperties());
        Assert.assertEquals(new HashSet<>(Arrays.asList("a", "b", "c", "d", "e")), structuresSource.getMapping("test").getSource().getExcludes());
    }

    @Test
    public void diffMapping() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .build(), new HashMap<>());
        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        Mappings diffMappings = structures.diffMappings(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .build());
        HashMap<String, Object> res = new HashMap<>();
        res.put("c", "d");
        res.put("f", "g");
        Assert.assertEquals(res, diffMappings.getProperties());
        diffMappings = structures.diffMappings(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties)
                    .build()
        );
        Assert.assertEquals(new HashMap<>(), diffMappings.getProperties());

        //test with source
        IndexStructures structuresSource = new IndexStructures();
        Mappings.Source source = new Mappings.Source();
        source.getExcludes().addAll(Arrays.asList("a", "b", "c"));
        structuresSource.putStructure(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties)
                            .source(source)
                            .build(), new HashMap<>());
        diffMappings = structuresSource.diffMappings(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .source(source)
                            .build());
        Assert.assertEquals(res, diffMappings.getProperties());
        diffMappings = structuresSource.diffMappings(
            "test", Mappings.builder()
                            .type(ElasticSearchClient.TYPE)
                            .properties(properties2)
                            .source(source)
                            .build());
        Assert.assertEquals(res, diffMappings.getProperties());
        Assert.assertNull("Mapping source should not be return by diffStructure()", diffMappings.getSource());
        diffMappings = structuresSource.diffMappings(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties)
                    .source(source)
                    .build()
        );
        Assert.assertEquals(new HashMap<>(), diffMappings.getProperties());
    }

    @Test
    public void containsMapping() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("a", "b");
        properties.put("c", "d");
        properties.put("f", "g");
        structures.putStructure("test", Mappings.builder()
                                                .type(ElasticSearchClient.TYPE)
                                                .properties(properties)
                                                .source(new Mappings.Source())
                                                .build(), new HashMap<>());

        HashMap<String, Object> properties2 = new HashMap<>();
        properties2.put("a", "b");
        properties2.put("c", "d");
        Assert.assertTrue(structures.containsMapping(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties2)
                    .source(new Mappings.Source())
                    .build()
        ));

        HashMap<String, Object> properties3 = new HashMap<>();
        properties3.put("a", "b");
        properties3.put("q", "d");
        Assert.assertFalse(structures.containsMapping(
            "test",
            Mappings.builder()
                    .type(ElasticSearchClient.TYPE)
                    .properties(properties3)
                    .build()
        ));
    }

    @Test
    public void compareIndexSetting() {
        IndexStructures structures = new IndexStructures();
        HashMap<String, Object> settings = new HashMap<>();
        HashMap<String, Object> indexSettings = new HashMap<>();
        settings.put("index", indexSettings);
        indexSettings.put("number_of_replicas", "1");
        indexSettings.put("number_of_shards", "1");
        structures.putStructure("test", new Mappings(), settings);

        HashMap<String, Object> settings2 = new HashMap<>();
        HashMap<String, Object> indexSettings2 = new HashMap<>();
        settings2.put("index", indexSettings2);
        indexSettings2.put("number_of_replicas", "1");
        indexSettings2.put("number_of_shards", "1");
        Assert.assertTrue(structures.compareIndexSetting(
            "test",
            settings2
        ));

        HashMap<String, Object> settings3 = new HashMap<>();
        HashMap<String, Object> indexSettings3 = new HashMap<>();
        settings3.put("index", indexSettings3);
        indexSettings3.put("number_of_replicas", "1");
        indexSettings3.put("number_of_shards", "2");
        Assert.assertFalse(structures.compareIndexSetting(
            "test",
            settings3
        ));
    }
}
