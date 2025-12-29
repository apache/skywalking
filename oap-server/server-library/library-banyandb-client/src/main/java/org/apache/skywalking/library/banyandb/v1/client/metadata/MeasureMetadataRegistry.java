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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.MeasureRegistryServiceGrpc;
import org.apache.skywalking.library.banyandb.v1.client.grpc.MetadataClient;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

public class MeasureMetadataRegistry extends MetadataClient<MeasureRegistryServiceGrpc.MeasureRegistryServiceBlockingStub,
        Measure> {

    public MeasureMetadataRegistry(Channel channel) {
        super(MeasureRegistryServiceGrpc.newBlockingStub(channel));
    }

    @Override
    public long create(final Measure payload) throws BanyanDBException {
        BanyandbDatabase.MeasureRegistryServiceCreateResponse resp = execute(() ->
                stub.create(BanyandbDatabase.MeasureRegistryServiceCreateRequest.newBuilder()
                        .setMeasure(payload)
                        .build()));
        return resp.getModRevision();
    }

    @Override
    public void update(final Measure payload) throws BanyanDBException {
        execute(() ->
                stub.update(BanyandbDatabase.MeasureRegistryServiceUpdateRequest.newBuilder()
                        .setMeasure(payload)
                        .build()));
    }

    @Override
    public boolean delete(final String group, final String name) throws BanyanDBException {
        BanyandbDatabase.MeasureRegistryServiceDeleteResponse resp = execute(() ->
                stub.delete(BanyandbDatabase.MeasureRegistryServiceDeleteRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return resp != null && resp.getDeleted();
    }

    @Override
    public Measure get(final String group, final String name) throws BanyanDBException {
        BanyandbDatabase.MeasureRegistryServiceGetResponse resp = execute(() ->
                stub.get(BanyandbDatabase.MeasureRegistryServiceGetRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));

        return resp.getMeasure();
    }

    @Override
    public ResourceExist exist(String group, String name) throws BanyanDBException {
        BanyandbDatabase.MeasureRegistryServiceExistResponse resp = execute(() ->
                stub.exist(BanyandbDatabase.MeasureRegistryServiceExistRequest.newBuilder()
                        .setMetadata(BanyandbCommon.Metadata.newBuilder().setGroup(group).setName(name).build())
                        .build()));
        return ResourceExist.builder().hasGroup(resp.getHasGroup()).hasResource(resp.getHasMeasure()).build();
    }

    @Override
    public List<Measure> list(final String group) throws BanyanDBException {
        BanyandbDatabase.MeasureRegistryServiceListResponse resp = execute(() ->
                stub.list(BanyandbDatabase.MeasureRegistryServiceListRequest.newBuilder()
                        .setGroup(group)
                        .build()));

        return resp.getMeasureList();
    }
}
