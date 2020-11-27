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

package org.apache.skywalking.oap.server.core.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.enumeration.MetricsType;

/**
 * Define the metrics provided in the OAP server.
 *
 * @since 8.3.0
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MetricDefinition {
    private String name;
    private MetricsType type;
    /**
     * Catalog includes SERVICE_CATALOG,SERVICE_INSTANCE_CATALOG,ENDPOINT_CATALOG,
     * SERVICE_RELATION_CATALOG,SERVICE_INSTANCE_RELATION_CATALOG_NAME,ENDPOINT_RELATION_CATALOG_NAME
     */
    private String catalog;
}
