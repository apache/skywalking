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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.oap.server.library.it.ITVersions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.io.IOException;

@Slf4j
@Testcontainers
public class BanyanDBClientTestCI {
    private static final String REGISTRY = "ghcr.io";
    private static final String IMAGE_NAME = "apache/skywalking-banyandb";
    private static final String TAG = ITVersions.get("SW_BANYANDB_COMMIT");

    private static final String IMAGE = REGISTRY + "/" + IMAGE_NAME + ":" + TAG;

    protected static final int GRPC_PORT = 17912;
    protected static final int HTTP_PORT = 17913;

    @Container
    public GenericContainer<?> banyanDB = new GenericContainer<>(
            DockerImageName.parse(IMAGE))
            .withCommand("standalone", "--stream-root-path", "/tmp/banyandb-stream-data",
                    "--measure-root-path", "/tmp/banyand-measure-data")
            .withExposedPorts(GRPC_PORT, HTTP_PORT)
            .waitingFor(Wait.forHttp("/api/healthz").forPort(HTTP_PORT));

    protected BanyanDBClient client;

    protected void setUpConnection() throws IOException {
        log.info("create BanyanDB client and try to connect");
        client = new BanyanDBClient(String.format("%s:%d", banyanDB.getHost(), banyanDB.getMappedPort(GRPC_PORT)));
        client.connect();
    }

    protected void closeClient() throws IOException {
        if (this.client != null) {
            this.client.close();
        }
    }

    protected BanyandbCommon.Group buildStreamGroup() {
        return BanyandbCommon.Group.newBuilder().setMetadata(BanyandbCommon.Metadata.newBuilder().setName("sw_record"))
                                   .setCatalog(BanyandbCommon.Catalog.CATALOG_STREAM)
                                   .setResourceOpts(BanyandbCommon.ResourceOpts.newBuilder()
                                                                               .setShardNum(2)
                                                                               .setSegmentInterval(
                                                                                   BanyandbCommon.IntervalRule.newBuilder()
                                                                                                              .setUnit(
                                                                                                                  BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                              .setNum(
                                                                                                                  1))
                                                                               .setTtl(
                                                                                   BanyandbCommon.IntervalRule.newBuilder()
                                                                                                              .setUnit(
                                                                                                                  BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                              .setNum(
                                                                                                                  3)))
                                   .build();
    }

    protected BanyandbCommon.Group buildMeasureGroup() {
        return BanyandbCommon.Group.newBuilder().setMetadata(BanyandbCommon.Metadata.newBuilder().setName("sw_metric"))
                                   .setCatalog(BanyandbCommon.Catalog.CATALOG_MEASURE)
                                   .setResourceOpts(BanyandbCommon.ResourceOpts.newBuilder()
                                                                               .setShardNum(2)
                                                                               .setSegmentInterval(
                                                                                   BanyandbCommon.IntervalRule.newBuilder()
                                                                                                              .setUnit(
                                                                                                                  BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                              .setNum(
                                                                                                                  1))
                                                                               .setTtl(
                                                                                   BanyandbCommon.IntervalRule.newBuilder()
                                                                                                              .setUnit(
                                                                                                                  BanyandbCommon.IntervalRule.Unit.UNIT_DAY)
                                                                                                              .setNum(
                                                                                                                  7)))
                                   .build();
    }
}
