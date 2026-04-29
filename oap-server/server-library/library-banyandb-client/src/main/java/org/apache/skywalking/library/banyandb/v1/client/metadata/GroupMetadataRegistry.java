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
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.GroupRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class GroupMetadataRegistry extends MetadataClient<GroupRegistryServiceGrpc.GroupRegistryServiceBlockingStub,
        Group> {

    public GroupMetadataRegistry(Channel channel) {
        super(GroupRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(Group payload) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.GroupRegistryServiceCreateRequest.newBuilder()
                        .setGroup(payload)
                        .build()));
        return resp == null ? DEFAULT_MOD_REVISION : resp.getModRevision();
    }

    @Override
    public void update(Group payload) throws BanyanDBException {
        updateWithRevision(payload);
    }

    @Override
    public long updateWithRevision(Group payload) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceUpdateResponse resp = execute(() ->
                stub.update(BanyandbDatabase.GroupRegistryServiceUpdateRequest.newBuilder()
                        .setGroup(payload)
                        .build()));
        return resp == null ? DEFAULT_MOD_REVISION : resp.getModRevision();
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.GroupRegistryServiceDeleteRequest.newBuilder()
                        .setGroup(name)
                        .build()));
        // Schema-consistency Phase 1+ proto removed the explicit `bool deleted` field;
        // a non-null response means the server accepted the delete. mod_revision is
        // the new authoritative signal for "tombstone recorded" — callers needing
        // delete-fence semantics should use AwaitSchemaDeleted via SchemaBarrier when
        // mod_revision is 0 (no tombstone).
        return resp != null;
    }

    @Override
    public Group get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.GroupRegistryServiceGetRequest.newBuilder()
                        .setGroup(name)
                        .build()));

        return resp.getGroup();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.GroupRegistryServiceExistRequest.newBuilder()
                        .setGroup(name)
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasGroup()).build();
    }

    @Override
    public List<Group> list(String group) throws BanyanDBException {
        BanyandbDatabase.GroupRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.GroupRegistryServiceListRequest.newBuilder()
                        .build()));

        return resp.getGroupList();
    }
}
