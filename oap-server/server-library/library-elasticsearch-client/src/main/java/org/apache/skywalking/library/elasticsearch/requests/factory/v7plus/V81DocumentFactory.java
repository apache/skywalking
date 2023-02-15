/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.elasticsearch.requests.factory.v7plus;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.isEmpty;
import static java.util.Objects.requireNonNull;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.ElasticSearchVersion;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.factory.DocumentFactory;
import com.google.common.collect.ImmutableMap;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpRequestBuilder;
import com.linecorp.armeria.common.MediaType;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

public class V81DocumentFactory implements DocumentFactory {
    private final ElasticSearchVersion version;

    @Delegate // Delegate all compatible methods to V7DocumentFactory and just override the incompatible ones.
    private final V7DocumentFactory v7DocumentFactory;

    public V81DocumentFactory(ElasticSearchVersion version) {
        this.version = version;
        this.v7DocumentFactory = new V7DocumentFactory(version);
    }

    @SneakyThrows
    @Override
    public HttpRequest update(UpdateRequest request, Map<String, ?> params) {
        requireNonNull(request, "request");

        final String index = request.getIndex();
        final String type = request.getType();
        final String id = request.getId();
        final Map<String, Object> doc = request.getDoc();

        checkArgument(!isNullOrEmpty(index), "index cannot be null or empty");
        checkArgument(!isNullOrEmpty(type), "type cannot be null or empty");
        checkArgument(!isNullOrEmpty(id), "id cannot be null or empty");
        checkArgument(doc != null && !isEmpty(doc.entrySet()), "doc cannot be null or empty");

        final HttpRequestBuilder builder = HttpRequest.builder();
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        final byte[] content = version.codec().encode(ImmutableMap.of("doc", doc));

        builder.post("/{index}/_update/{id}")
               .pathParam("index", index)
               .pathParam("id", id)
               .content(MediaType.JSON, content);

        return builder.build();
    }
}
