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

package org.apache.skywalking.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;

/**
 * TraceQueryResponse represents the trace query result.
 */
public class TraceQueryResponse {
    @Getter
    private List<RowEntity> entities;

    TraceQueryResponse(BanyandbTrace.QueryResponse response) {
        final List<BanyandbTrace.Entity> entitiesList = response.getEntitiesList();
        entities = new ArrayList<>(entitiesList.size());
        entitiesList.forEach(entity -> entities.add(new RowEntity(entity)));
    }

    /**
     * @return size of the response set.
     */
    public int size() {
        return entities.size();
    }
}
