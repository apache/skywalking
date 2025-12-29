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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property;
import org.apache.skywalking.banyandb.database.v1.PropertyRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class PropertyMetadataRegistry extends MetadataClient<PropertyRegistryServiceGrpc.PropertyRegistryServiceBlockingStub,
        Property> {

    public PropertyMetadataRegistry(Channel channel) {
        super(PropertyRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(final Property payload) throws BanyanDBException {
        BanyandbDatabase.PropertyRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.PropertyRegistryServiceCreateRequest.newBuilder()
                        .setProperty(payload)
                        .build()));
        return resp.getModRevision();
    }

    @Override
    public void update(final Property payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.PropertyRegistryServiceUpdateRequest.newBuilder()
                        .setProperty(payload)
                        .build()));
    }

    @Override
    public boolean delete(final String group, final String name) throws BanyanDBException {
        BanyandbDatabase.PropertyRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.PropertyRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public Property get(final String group, final String name) throws BanyanDBException {
        BanyandbDatabase.PropertyRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.PropertyRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getProperty();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.PropertyRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.PropertyRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasProperty()).build();
    }

    @Override
    public List<Property> list(final String group) throws BanyanDBException {
        BanyandbDatabase.PropertyRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.PropertyRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getPropertiesList();
    }
}
