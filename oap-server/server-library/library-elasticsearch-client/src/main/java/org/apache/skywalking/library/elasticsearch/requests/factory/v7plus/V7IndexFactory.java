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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.factory.IndexFactory;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

import static com.google.common.base.Preconditions.checkArgument;

@RequiredArgsConstructor
final class V7IndexFactory implements IndexFactory {
    private final ElasticSearchVersion version;

    @Override
    public HttpRequest exists(String index) {
        checkArgument(!Strings.isNullOrEmpty(index), "index cannot be null or empty");

        return HttpRequest.builder()
                          .head("/{index}")
                          .pathParam("index", index)
                          .build();
    }

    @Override
    public HttpRequest get(final String index) {
        checkArgument(!Strings.isNullOrEmpty(index), "index cannot be null or empty");

        return HttpRequest.builder()
                          .get("/{index}")
                          .pathParam("index", index)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest create(String index,
                              Mappings mappings,
                              Map<String, ?> settings) {
        checkArgument(!Strings.isNullOrEmpty(index), "index cannot be null or empty");

        final ImmutableMap.Builder<String, Object> bodyBuilder = ImmutableMap.builder();
        if (mappings != null) {
            bodyBuilder.put("mappings", mappings);
        }
        if (settings != null) {
            bodyBuilder.put("settings", settings);
        }
        final ImmutableMap<String, Object> body = bodyBuilder.build();
        final byte[] content = version.codec().encode(body);

        return HttpRequest.builder()
                          .put("/{index}")
                          .pathParam("index", index)
                          .content(MediaType.JSON, content)
                          .build();
    }

    @Override
    public HttpRequest delete(String index) {
        checkArgument(!Strings.isNullOrEmpty(index), "index cannot be null or empty");

        return HttpRequest.builder()
                          .delete("/{index}")
                          .pathParam("index", index)
                          .build();
    }

    @SneakyThrows
    @Override
    public HttpRequest putMapping(String index, String type, Mappings mapping) {
        checkArgument(!Strings.isNullOrEmpty(index), "index cannot be null or empty");

        final byte[] content = version.codec().encode(mapping);
        return HttpRequest.builder()
                          .put("/{index}/_mapping")
                          .pathParam("index", index)
                          .content(MediaType.JSON, content)
                          .build();
    }
}
