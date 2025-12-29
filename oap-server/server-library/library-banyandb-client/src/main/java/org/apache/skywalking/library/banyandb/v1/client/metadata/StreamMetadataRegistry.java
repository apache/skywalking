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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.StreamRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class StreamMetadataRegistry extends MetadataClient<StreamRegistryServiceGrpc.StreamRegistryServiceBlockingStub,
        Stream> {

    public StreamMetadataRegistry(Channel channel) {
        super(StreamRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(Stream payload) throws BanyanDBException {
        BanyandbDatabase.StreamRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.StreamRegistryServiceCreateRequest.newBuilder()
                        .setStream(payload)
                        .build()));
        return resp.getModRevision();
    }

    @Override
    public void update(Stream payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.StreamRegistryServiceUpdateRequest.newBuilder()
                        .setStream(payload)
                        .build()));
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        BanyandbDatabase.StreamRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.StreamRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public Stream get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.StreamRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.StreamRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getStream();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.StreamRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.StreamRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasStream()).build();
    }

    @Override
    public List<Stream> list(String group) throws BanyanDBException {
        BanyandbDatabase.StreamRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.StreamRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getStreamList();
    }
}
