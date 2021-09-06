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

package org.apache.skywalking.library.elasticsearch.requests.search;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public final class RangeQueryBuilder implements QueryBuilder {
    private final String name;
    private Object gte;
    private Object gt;
    private Object lte;
    private Object lt;
    private Double boost;

    RangeQueryBuilder(String name) {
        checkArgument(!Strings.isNullOrEmpty(name), "name cannot be null or empty");

        this.name = name;
    }

    public RangeQueryBuilder gte(Object gte) {
        this.gte = requireNonNull(gte, "gte");
        return this;
    }

    public RangeQueryBuilder gt(Object gt) {
        this.gt = requireNonNull(gt, "gt");
        return this;
    }

    public RangeQueryBuilder lte(Object lte) {
        this.lte = requireNonNull(lte, "lte");
        return this;
    }

    public RangeQueryBuilder lt(Object lt) {
        this.lt = requireNonNull(lt, "lt");
        return this;
    }

    public RangeQueryBuilder boost(Double boost) {
        requireNonNull(boost, "boost");

        this.boost = boost;
        return this;
    }

    @Override
    public Query build() {
        return new RangeQuery(name, gte, gt, lte, lt, boost);
    }
}
