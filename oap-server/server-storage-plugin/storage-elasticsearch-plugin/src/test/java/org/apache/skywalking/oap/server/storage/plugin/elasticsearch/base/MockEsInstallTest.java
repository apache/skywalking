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
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MockEsInstallTest {
    private final ObjectMapper mapper = new ObjectMapper();

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

    public void init(String name,
                     Mappings hisMappings,
                     Mappings newMappings,
                     Set<String> excludes,
                     Set<String> newExcludes,
                     String combineResult,
                     String diffResult,
                     boolean contains) {
        if (excludes != null) {
            hisMappings.getSource().setExcludes(excludes);
        }
        if (newExcludes != null) {
            newMappings.getSource().setExcludes(newExcludes);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void mockEsInstallTest(String name,
                                  Mappings hisMappings,
                                  Mappings newMappings,
                                  Set<String> excludes,
                                  Set<String> newExcludes,
                                  String combineResult,
                                  String diffResult,
                                  boolean contains) throws JsonProcessingException {
        init(name, hisMappings, newMappings, excludes, newExcludes, combineResult, diffResult, contains);

        IndexStructures structures = new IndexStructures();
        //clone it since the items will be changed after combine
        Mappings hisMappingsClone = cloneMappings(hisMappings);
        //put the current mappings
        structures.putStructure(name, hisMappings, new HashMap<>());
        //if current mappings already contains new mappings items
        Assertions.assertEquals(contains, structures.containsMapping(name, newMappings));

        //put the new mappings and combine
        structures.putStructure(name, newMappings, new HashMap<>());
        Mappings mappings = structures.getMapping(name);
        Assertions.assertEquals(combineResult, mapper.writeValueAsString(mappings));

        //diff the hisMapping and new, if it has new item will update current index
        structures.putStructure(name, newMappings, new HashMap<>());
        Mappings diff = structures.diffMappings(name, hisMappingsClone);
        Assertions.assertEquals(diffResult, mapper.writeValueAsString(diff));
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
