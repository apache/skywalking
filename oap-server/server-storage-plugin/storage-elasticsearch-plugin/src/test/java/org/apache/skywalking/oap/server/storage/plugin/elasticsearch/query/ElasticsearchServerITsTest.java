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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storage ITs of every area run on every server: each server IT declares them as nested classes of its own, so a
 * report names the server a test ran on, and this test fails when a server IT misses an area another one runs.
 */
public class ElasticsearchServerITsTest {
    private static final List<Class<?>> SERVER_ITS = Arrays.asList(
        Elasticsearch8IT.class, Elasticsearch9IT.class, Elasticsearch9ShardedIT.class,
        OpenSearch1IT.class, OpenSearch2IT.class, OpenSearch3IT.class, OpenSearch3ShardedIT.class);

    @Test
    public void runsEveryAreaOnEveryServer() {
        final Set<String> expected = areas(SERVER_ITS.get(0));
        for (final Class<?> serverIT : SERVER_ITS) {
            assertEquals(expected, areas(serverIT), serverIT.getSimpleName());
        }
    }

    private static Set<String> areas(final Class<?> serverIT) {
        return Arrays.stream(serverIT.getDeclaredClasses())
                     .filter(nested -> nested.isAnnotationPresent(Nested.class))
                     .map(nested -> nested.getSimpleName() + " runs " + nested.getSuperclass().getSimpleName())
                     .collect(Collectors.toCollection(TreeSet::new));
    }
}
