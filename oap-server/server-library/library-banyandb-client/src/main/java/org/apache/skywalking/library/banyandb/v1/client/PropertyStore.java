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

import io.grpc.Channel;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.ApplyRequest;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.ApplyRequest.Strategy;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.ApplyResponse;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.DeleteResponse;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.banyandb.property.v1.PropertyServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.HandleExceptionsWith;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class PropertyStore {
    private final PropertyServiceGrpc.PropertyServiceBlockingStub stub;

    public PropertyStore(Channel channel) {
        this.stub = PropertyServiceGrpc.newBlockingStub(channel);
    }

    public ApplyResponse apply(Property payload) throws BanyanDBException {
        return apply(payload, Strategy.STRATEGY_MERGE);
    }

    public ApplyResponse apply(Property payload, Strategy strategy) throws BanyanDBException {
        Strategy s = Strategy.STRATEGY_MERGE;
        ApplyRequest r = ApplyRequest.newBuilder()
                .setProperty(payload)
                .setStrategy(strategy)
                .build();
        return HandleExceptionsWith.callAndTranslateApiException(() ->
                this.stub.apply(r));
    }

    public DeleteResponse delete(String group, String name, String id) throws BanyanDBException {
        return HandleExceptionsWith.callAndTranslateApiException(() ->
                this.stub.delete(BanyandbProperty.DeleteRequest.newBuilder()
                        .setGroup(group)
                        .setName(name)
                        .setId(id)
                        .build()));
    }

    public BanyandbProperty.QueryResponse query(BanyandbProperty.QueryRequest req) throws BanyanDBException {
        return HandleExceptionsWith.callAndTranslateApiException(() ->
                this.stub.query(req));
    }
}
