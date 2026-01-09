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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Trace;
import org.apache.skywalking.banyandb.database.v1.TraceRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class TraceMetadataRegistry extends MetadataClient<TraceRegistryServiceGrpc.TraceRegistryServiceBlockingStub,
        Trace> {

    public TraceMetadataRegistry(Channel channel) {
        super(TraceRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(Trace payload) throws BanyanDBException {
        BanyandbDatabase.TraceRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.TraceRegistryServiceCreateRequest.newBuilder()
                        .setTrace(payload)
                        .build()));
        return resp.getModRevision();
    }

    @Override
    public void update(Trace payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.TraceRegistryServiceUpdateRequest.newBuilder()
                        .setTrace(payload)
                        .build()));
    }

    @Override
    public boolean delete(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TraceRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.TraceRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public Trace get(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TraceRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.TraceRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getTrace();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.TraceRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.TraceRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasTrace()).build();
    }

    @Override
    public List<Trace> list(String group) throws BanyanDBException {
        BanyandbDatabase.TraceRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.TraceRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getTraceList();
    }
}