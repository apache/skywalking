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

public interface IndexFactory {
    /**
     * Returns a request to check whether the {@code index} exists or not.
     */
    HttpRequest exists(String index);

    /**
     * Returns a request to get an index of name {@code index}.
     */
    HttpRequest get(String index);

    /**
     * Returns a request to create an index of name {@code index}.
     */
    HttpRequest create(String index,
                       Mappings mappings,
                       Map<String, ?> settings);

    /**
     * Returns a request to delete an index of name {@code index}.
     */
    HttpRequest delete(String index);

    /**
     * Returns a request to update the {@code mappings} of an index of name {@code index}.
     */
    HttpRequest putMapping(String index, String type, Mappings mappings);
}
