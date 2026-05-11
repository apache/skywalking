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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One row of {@code GET /inspect/entities}. {@code decoded} carries the
 * scope-specific human-readable shape (single entity or source/destination
 * pair); {@code layer} surfaces the layer of the source service for the
 * row's scope (multi-layer services emit one row per layer);
 * {@code mqeEntity} is the MQE-ready payload.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityRow {
    private final String entityId;
    private final Map<String, Object> decoded;
    private final String layer;
    private final MqeEntity mqeEntity;
}
