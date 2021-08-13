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

import org.apache.skywalking.banyandb.v1.Banyandb;

import static org.apache.skywalking.banyandb.v1.Banyandb.PairQuery.BinaryOp.BINARY_OP_EQ;

/**
 * PairQuery represents a query condition, including field name, operator, and value(s);
 */
public abstract class PairQueryCondition {
    protected String fieldName;

    protected abstract Banyandb.PairQuery build();

    /**
     * LongEqual represents `Field == value` condition.
     */
    public class LongEqual extends PairQueryCondition {
        private final long value;

        public LongEqual(String fieldName, long value) {
            this.fieldName = fieldName;
            this.value = value;
        }

        @Override
        protected Banyandb.PairQuery build() {
            return Banyandb.PairQuery.newBuilder()
                                     .setOp(BINARY_OP_EQ)
                                     .setCondition(
                                         Banyandb.TypedPair.newBuilder()
                                                           .setIntPair(
                                                               Banyandb.IntPair.newBuilder()
                                                                               .setKey(fieldName)
                                                                               .setValue(value)))
                                     .build();
        }
    }

    //TODO, Add all conditions.
}
