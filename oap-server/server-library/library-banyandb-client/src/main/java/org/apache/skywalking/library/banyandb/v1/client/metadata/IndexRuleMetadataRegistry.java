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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.IndexRuleRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class IndexRuleMetadataRegistry extends MetadataClient<IndexRuleRegistryServiceGrpc.IndexRuleRegistryServiceBlockingStub,
        IndexRule> {
    public IndexRuleMetadataRegistry(Channel channel) {
        super(IndexRuleRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(IndexRule payload) throws BanyanDBException {
        execute(() ->
                stub.create(BanyandbDatabase.IndexRuleRegistryServiceCreateRequest.newBuilder()
                        .setIndexRule(payload)
                        .build()));
        return DEFAULT_MOD_REVISION;
    }

    @Override
    public void update(IndexRule payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.IndexRuleRegistryServiceUpdateRequest.newBuilder()
                        .setIndexRule(payload)
                        .build()));
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.IndexRuleRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public IndexRule get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.IndexRuleRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getIndexRule();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.IndexRuleRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasIndexRule()).build();
    }

    @Override
    public List<IndexRule> list(String group) throws BanyanDBException {
        BanyandbDatabase.IndexRuleRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.IndexRuleRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getIndexRuleList();
    }
}
