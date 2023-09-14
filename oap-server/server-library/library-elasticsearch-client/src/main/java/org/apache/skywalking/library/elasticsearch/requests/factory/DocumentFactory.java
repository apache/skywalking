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

package org.apache.skywalking.library.elasticsearch.requests.factory;

import com.linecorp.armeria.common.HttpRequest;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.requests.IndexRequest;
import org.apache.skywalking.library.elasticsearch.requests.UpdateRequest;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;

public interface DocumentFactory {
    /**
     * Returns a request to check whether the document exists in the {@code index} or not.
     */
    HttpRequest exist(String index, String type, String id);

    /**
     * Returns a request to get a document of {@code id} in {@code index}.
     */
    HttpRequest get(String index, String type, String id);

    /**
     * Returns a request to get multiple documents of {@code ids} in {@code index}.
     */
    HttpRequest mget(String index, String type, Iterable<String> ids);

    /**
     * Returns a request to get multiple documents of {@code indexIds}.
     */
    HttpRequest mget(final String type, final Map<String, List<String>> indexIds);

    /**
     * Returns a request to index a document with {@link IndexRequest}.
     */
    HttpRequest index(IndexRequest request, Map<String, ?> params);

    /**
     * Returns a request to update a document with {@link UpdateRequest}.
     */
    HttpRequest update(UpdateRequest request, Map<String, ?> params);

    /**
     * Returns a request to delete documents matching the given {@code query} in {@code index}.
     */
    HttpRequest delete(String index, String type, Query query,
                       Map<String, ?> params);

    /**
     * Returns a request to delete documents matching the given {@code id} in {@code index}.
     */
    HttpRequest deleteById(String index, String type, String id, Map<String, ?> params);
}
