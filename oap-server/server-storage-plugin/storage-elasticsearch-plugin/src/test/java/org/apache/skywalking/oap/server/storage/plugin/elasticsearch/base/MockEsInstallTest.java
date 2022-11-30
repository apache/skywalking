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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

@RunWith(Parameterized.class)
public class MockEsInstallTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public Mappings hisMappings;

    @Parameterized.Parameter(2)
    public Mappings newMappings;

    @Parameterized.Parameter(3)
    public Set<String> excludes;

    @Parameterized.Parameter(4)
    public Set<String> newExcludes;

    @Parameterized.Parameter(5)
    public String combineResult;

    @Parameterized.Parameter(6)
    public String diffResult;

    @Parameterized.Parameter(7)
    public boolean contains;

    @Parameterized.Parameters(name = "{index}: {0}")

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "contains_properties_true",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword"))))
                        .source(new Mappings.Source())
                    .build(),
                null,
                null,
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"}},\"_source\":{\"excludes\":[]}}",
                "{\"properties\":{},\"_source\":null}",
                true
            },
            {
                "contains_source_true",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword"))))
                        .source(new Mappings.Source())
                    .build(),
                new HashSet<>(ImmutableSet.of("b")),
                new HashSet<>(ImmutableSet.of("b")),
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"}},\"_source\":{\"excludes\":[\"b\"]}}",
                "{\"properties\":{},\"_source\":null}",
                true
            },
            {
                "contains_source_false",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword"))))
                        .source(new Mappings.Source())
                    .build(),
                new HashSet<>(ImmutableSet.of("a", "b")),
                new HashSet<>(),
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"}},\"_source\":{\"excludes\":[\"a\"]}}",
                "{\"properties\":{},\"_source\":null}",
                false
            },
            {
                "contains_source_false",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("c", ImmutableMap.of("type", "keyword"))))
                        .source(new Mappings.Source())
                    .build(),
                new HashSet<>(ImmutableSet.of("a", "b")),
                new HashSet<>(ImmutableSet.of("c")),
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"},\"c\":{\"type\":\"keyword\"}}," +
                    "\"_source\":{\"excludes\":[\"a\",\"b\",\"c\"]}}",
                "{\"properties\":{\"c\":{\"type\":\"keyword\"}},\"_source\":null}",
                false
            },
            {
                "combineAndUpdate_properties",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword", "index", false),
                                            "c", ImmutableMap.of("type", "keyword")
                            )))
                        .source(new Mappings.Source())
                    .build(),
                null,
                null,
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\",\"index\":false},\"c\":{\"type\":\"keyword\"}},\"" +
                    "_source\":{\"excludes\":[]}}",
                "{\"properties\":{\"c\":{\"type\":\"keyword\"}},\"_source\":null}",
                false
            },
            {
                "combineAndUpdate_properties",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword", "index", false)
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword"),
                                            "c", ImmutableMap.of("type", "keyword", "index", false)
                            )))
                        .source(new Mappings.Source())
                    .build(),
                null,
                null,
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"},\"c\":{\"type\":\"keyword\",\"index\":false}},\"" +
                    "_source\":{\"excludes\":[]}}",
                "{\"properties\":{\"c\":{\"type\":\"keyword\",\"index\":false}},\"_source\":null}",
                false
            },
            {
                "update_source",
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("a", ImmutableMap.of("type", "keyword"),
                                            "b", ImmutableMap.of("type", "keyword", "index", false)
                            )))
                        .source(new Mappings.Source())
                    .build(),
                Mappings.builder()
                        .type(ElasticSearchClient.TYPE)
                        .properties(new HashMap<>(
                            ImmutableMap.of("b", ImmutableMap.of("type", "keyword"),
                                            "c", ImmutableMap.of("type", "keyword", "index", false)
                            )))
                        .source(new Mappings.Source())
                    .build(),
                new HashSet<>(ImmutableSet.of("a")),
                new HashSet<>(ImmutableSet.of("b")),
                "{\"properties\":{\"a\":{\"type\":\"keyword\"},\"b\":{\"type\":\"keyword\"},\"c\":{\"type\":\"keyword\",\"index\":false}}," +
                    "\"_source\":{\"excludes\":[\"a\",\"b\"]}}",
                "{\"properties\":{\"c\":{\"type\":\"keyword\",\"index\":false}},\"_source\":null}",
                false
            }
        });
    }

    @Before
    public void init() {
        if (this.excludes != null) {
            this.hisMappings.getSource().setExcludes(this.excludes);
        }
        if (this.newExcludes != null) {
            this.newMappings.getSource().setExcludes(this.newExcludes);
        }
    }

    @Test
    public void mockEsInstallTest() throws JsonProcessingException {
        IndexStructures structures = new IndexStructures();
        //clone it since the items will be changed after combine
        Mappings hisMappingsClone = cloneMappings(this.hisMappings);
        //put the current mappings
        structures.putStructure(this.name, this.hisMappings, new HashMap<>());
        //if current mappings already contains new mappings items
        Assert.assertEquals(this.contains, structures.containsMapping(this.name, this.newMappings));

        //put the new mappings and combine
        structures.putStructure(this.name, this.newMappings, new HashMap<>());
        Mappings mappings = structures.getMapping(this.name);
        Assert.assertEquals(this.combineResult, mapper.writeValueAsString(mappings));

        //diff the hisMapping and new, if it has new item will update current index
        structures.putStructure(this.name, this.newMappings, new HashMap<>());
        Mappings diff = structures.diffMappings(this.name, hisMappingsClone);
        Assert.assertEquals(this.diffResult, mapper.writeValueAsString(diff));
    }

    private Mappings cloneMappings(Mappings mappings) {
        Mappings.Source source = new Mappings.Source();
        source.setExcludes(new HashSet<>(mappings.getSource().getExcludes()));
        return Mappings.builder()
                       .type(mappings.getType())
                       .properties(new HashMap<>(mappings.getProperties()))
                       .source(source)
                       .build();
    }
}
