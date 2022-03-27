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
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

public interface TemplateFactory {
    /**
     * Returns a request to check whether the template exists or not.
     */
    HttpRequest exists(String name);

    /**
     * Returns a request to get a template of {@code name}.
     */
    HttpRequest get(String name);

    /**
     * Returns a request to delete a template of {@code name}.
     */
    HttpRequest delete(String name);

    /**
     * Returns a request to create or update a template of {@code name} with the given {@code
     * settings}, {@code mappings} and {@code order}.
     */
    HttpRequest createOrUpdate(String name, Map<String, ?> settings,
                               Mappings mappings, int order);
}
