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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRuleBinding;
import org.apache.skywalking.banyandb.database.v1.IndexRuleBindingRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class IndexRuleBindingMetadataRegistry extends MetadataClient<IndexRuleBindingRegistryServiceGrpc.IndexRuleBindingRegistryServiceBlockingStub,
        IndexRuleBinding> {

    public IndexRuleBindingMetadataRegistry(Channel channel) {
        super(IndexRuleBindingRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(IndexRuleBinding payload) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.IndexRuleBindingRegistryServiceCreateRequest.newBuilder()
                        .setIndexRuleBinding(payload)
                        .build()));
        return resp == null ? DEFAULT_MOD_REVISION : resp.getModRevision();
    }

    @Override
    public void update(IndexRuleBinding payload) throws BanyanDBException {
        updateWithRevision(payload);
    }

    @Override
    public long updateWithRevision(IndexRuleBinding payload) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceUpdateResponse resp = execute(() ->
                stub.update(BanyandbDatabase.IndexRuleBindingRegistryServiceUpdateRequest.newBuilder()
                        .setIndexRuleBinding(payload)
                        .build()));
        return resp == null ? DEFAULT_MOD_REVISION : resp.getModRevision();
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        return deleteWithRevision(group, name) >= 0;
    }

    @Override
    public long deleteWithRevision(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.IndexRuleBindingRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp == null ? DEFAULT_MOD_REVISION : resp.getModRevision();
    }

    @Override
    public IndexRuleBinding get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.IndexRuleBindingRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getIndexRuleBinding();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.IndexRuleBindingRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasIndexRuleBinding()).build();
    }

    @Override
    public List<IndexRuleBinding> list(String group) throws BanyanDBException {
        BanyandbDatabase.IndexRuleBindingRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.IndexRuleBindingRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getIndexRuleBindingList();
    }
}
