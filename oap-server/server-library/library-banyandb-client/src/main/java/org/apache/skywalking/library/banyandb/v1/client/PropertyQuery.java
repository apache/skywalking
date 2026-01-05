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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Setter;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

/**
 * PropertyQuery is the high-level query API for the property model.
 */
@Setter
public class PropertyQuery extends AbstractQuery<BanyandbProperty.QueryRequest> {
    /**
     * The limit size of the query. Default value is 20.
     */
    private int limit;
    
    /**
     * Specific property IDs to query
     */
    private List<String> ids;
    
    /**
     * Construct a property query with required fields
     */
    public PropertyQuery(final List<String> groups, final String name, final Set<String> projections) {
        super(groups, name, null, projections);
        this.limit = 20;
        this.ids = new ArrayList<>();
    }

    /**
     * Add a property ID to filter query results
     * @param id property ID
     * @return this query instance for chaining
     */
    public PropertyQuery id(String id) {
        if (id != null && !id.isEmpty()) {
            this.ids.add(id);
        }
        return this;
    }
    
    /**
     * Add multiple property IDs to filter query results
     * @param ids list of property IDs
     * @return this query instance for chaining
     */
    public PropertyQuery ids(List<String> ids) {
        if (ids != null) {
            this.ids.addAll(ids);
        }
        return this;
    }
    
    @Override
    public PropertyQuery and(PairQueryCondition<?> condition) {
        return (PropertyQuery) super.and(condition);
    }

    @Override
    public PropertyQuery or(PairQueryCondition<?> condition) {
        return (PropertyQuery) super.or(condition);
    }

    public BanyandbProperty.QueryRequest build() throws BanyanDBException {
        final BanyandbProperty.QueryRequest.Builder builder = BanyandbProperty.QueryRequest.newBuilder();
        builder.setName(this.name);
        builder.addAllGroups(this.groups);
        builder.addAllTagProjection(this.tagProjections.keySet());
        buildCriteria().ifPresent(builder::setCriteria);
        builder.setLimit(this.limit);
        builder.setTrace(this.trace);
        
        if (!this.ids.isEmpty()) {
            builder.addAllIds(this.ids);
        }
        
        return builder.build();
    }
}
