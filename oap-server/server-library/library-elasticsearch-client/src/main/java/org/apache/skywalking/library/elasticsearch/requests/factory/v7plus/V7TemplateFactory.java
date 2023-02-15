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

package org.apache.skywalking.library.elasticsearch.requests.factory.v7plus;

import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.factory.TemplateFactory;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

@RequiredArgsConstructor
final class V7TemplateFactory implements TemplateFactory {
    private final ElasticSearchVersion version;

    @Override
    public HttpRequest exists(String name) {
        return HttpRequest.builder()
                          .get("/_template/{name}")
                          .pathParam("name", name)
                          .build();
    }

    @Override
    public HttpRequest get(final String name) {
        return HttpRequest.builder()
                          .get("/_template/{name}")
                          .pathParam("name", name)
                          .build();
    }

    @Override
    public HttpRequest delete(final String name) {
        return HttpRequest.builder()
                          .delete("/_template/{name}")
                          .pathParam("name", name)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest createOrUpdate(String name, Map<String, ?> settings,
                                      Mappings mappings, int order) {
        final String[] patterns = new String[] {name + "-*"};
        final Map<String, Object> aliases = ImmutableMap.of(name, Collections.emptyMap());
        final Map<String, Object> template =
            ImmutableMap.<String, Object>builder()
                        .put("index_patterns", patterns)
                        .put("aliases", aliases)
                        .put("settings", settings)
                        .put("mappings", mappings)
                        .build();

        final byte[] content = version.codec().encode(template);

        return HttpRequest.builder()
                          .put("/_template/{name}")
                          .pathParam("name", name)
                          .content(MediaType.JSON, content)
                          .build();
    }
}
