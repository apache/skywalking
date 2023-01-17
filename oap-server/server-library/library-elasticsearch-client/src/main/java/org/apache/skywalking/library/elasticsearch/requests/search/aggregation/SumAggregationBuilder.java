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

package org.apache.skywalking.library.elasticsearch.requests.search.aggregation;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

public final class SumAggregationBuilder implements AggregationBuilder {
    private final String name;

    private String field;

    SumAggregationBuilder(String name) {
        this.name = name;
    }

    public SumAggregationBuilder field(String field) {
        checkArgument(!Strings.isNullOrEmpty(field), "field cannot be blank");
        this.field = field;
        return this;
    }

    @Override
    public SumAggregation build() {
        return new SumAggregation(name, field);
    }
}
