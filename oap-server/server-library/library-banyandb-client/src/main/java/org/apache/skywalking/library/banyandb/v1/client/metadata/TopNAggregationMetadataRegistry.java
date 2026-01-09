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

package org.apache.skywalking.library.banyandb.v1.client.metadata;

import io.grpc.Channel;
import java.util.List;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.database.v1.TopNAggregationRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class TopNAggregationMetadataRegistry extends MetadataClient<TopNAggregationRegistryServiceGrpc.TopNAggregationRegistryServiceBlockingStub,
        TopNAggregation> {
    public TopNAggregationMetadataRegistry(Channel channel) {
        super(TopNAggregationRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(TopNAggregation payload) throws BanyanDBException {
        execute(() ->
                stub.create(BanyandbDatabase.TopNAggregationRegistryServiceCreateRequest.newBuilder()
                        .setTopNAggregation(payload)
                        .build()));
        return DEFAULT_MOD_REVISION;
    }

    @Override
    public void update(TopNAggregation payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.TopNAggregationRegistryServiceUpdateRequest.newBuilder()
                        .setTopNAggregation(payload)
                        .build()));
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TopNAggregationRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.TopNAggregationRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public TopNAggregation get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TopNAggregationRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.TopNAggregationRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getTopNAggregation();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TopNAggregationRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.TopNAggregationRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasTopNAggregation()).build();
    }

    @Override
    public List<TopNAggregation> list(String group) throws BanyanDBException {
        BanyandbDatabase.TopNAggregationRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.TopNAggregationRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getTopNAggregationList();
    }
}
