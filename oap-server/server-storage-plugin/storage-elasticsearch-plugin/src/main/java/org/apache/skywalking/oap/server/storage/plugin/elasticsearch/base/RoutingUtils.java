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

import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.util.Map;
import java.util.Optional;

public class RoutingUtils {

    public static Optional<String> getRouting(final Model model, final Map<String, Object> builder) {
        Optional<String> routingField = model.getElasticSearchModelExtension().getRouting();
        return routingField.map(v -> extractRoutingValue(v, builder));
    }

    private static String extractRoutingValue(String routingField, Map<String, Object> builder) {
        Object value = builder.get(routingField);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
