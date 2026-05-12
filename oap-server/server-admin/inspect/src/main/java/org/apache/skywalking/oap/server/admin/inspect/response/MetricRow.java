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

package org.apache.skywalking.oap.server.admin.inspect.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One row of {@code GET /inspect/metrics}. Each registered metric in
 * {@code ValueColumnMetadata} surfaces as one row, except entries that are
 * filtered out (NOT_VALUE persistent-but-not-queryable columns, and
 * {@code Scope.All} deprecated entries).
 */
@Getter
@AllArgsConstructor
public class MetricRow {
    private final String name;
    private final String type;
    private final String catalog;
    private final int scopeId;
    private final String scope;
    private final String valueColumnName;
    private final List<String> downsamplings;
}
