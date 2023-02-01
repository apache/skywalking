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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import org.apache.skywalking.library.elasticsearch.requests.search.SearchParams;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

import java.util.Optional;

public class RoutingUtils {

    public static void addRoutingValueToSearchParam(SearchParams searchParams, String routingValue) {
        if (!IndexController.INSTANCE.isEnableCustomRouting()) {
            return;
        }
        searchParams.routing(routingValue);
    }

    public static void addRoutingValuesToSearchParam(SearchParams searchParams, Iterable<String> routingValues) {
        if (!IndexController.INSTANCE.isEnableCustomRouting()) {
            return;
        }
        searchParams.routing(routingValues);
    }

    /**
     * get the value of the field annotated {@link ElasticSearch.Routing}
     */
    public static Optional<String> getRoutingValue(final Model model, final ElasticSearchConverter.ToStorage toStorage) {
        if (!IndexController.INSTANCE.isEnableCustomRouting()) {
            return Optional.empty();
        }
        Optional<String> routingField = model.getElasticSearchModelExtension().getRouting();
        return routingField.map(v -> extractRoutingValue(v, toStorage));
    }

    private static String extractRoutingValue(String routingField, ElasticSearchConverter.ToStorage toStorage) {
        Object value = toStorage.get(routingField);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
