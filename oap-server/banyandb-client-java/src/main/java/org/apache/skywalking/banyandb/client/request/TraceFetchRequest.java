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

package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * TraceFetchRequest can be used for fetching trace entities with the given traceId
 */
@Builder
@Getter
public class TraceFetchRequest {
    /**
     * traceId you want to search which is required
     */
    private final String traceId;

    /**
     * While searching for entities, you are able to specify fields being returned.
     * Projections must only contain valid field names defined in the schema.
     * Normally, for a complete entity fetch, user can also give a hint (i.e. add project "data_binary")
     * to ask BanyanBD to return binary part of the entity.
     */
    @Singular
    private final List<String> projections;
}
