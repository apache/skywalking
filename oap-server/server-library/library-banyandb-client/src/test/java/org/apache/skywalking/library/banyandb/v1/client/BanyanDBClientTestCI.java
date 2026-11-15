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
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.library.it.BanyanDBTestContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Testcontainers
public class BanyanDBClientTestCI {
    protected static final int GRPC_PORT = BanyanDBTestContainer.GRPC_PORT;
    protected static final int HTTP_PORT = BanyanDBTestContainer.HTTP_PORT;

    /**
     * The groups a BanyanDB server creates for its own bookkeeping at boot (its
     * {@code api/common/internal_group.go}), listed by {@code findGroups} beside the user's.
     */
    private static final Set<String> SERVER_GROUPS = Set.of("_deletion_task", "_property_gossip");

    @Container
    public GenericContainer<?> banyanDB = new GenericContainer<>(
            DockerImageName.parse(BanyanDBTestContainer.image()))
            .withCommand(BanyanDBTestContainer.standaloneCommand())
            .withExposedPorts(GRPC_PORT, HTTP_PORT)
            .waitingFor(Wait.forHttp(BanyanDBTestContainer.HEALTH_ENDPOINT).forPort(HTTP_PORT));

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

    /** The groups the user defined: {@code findGroups} without the server's own. */
    protected static List<BanyandbCommon.Group> userGroups(BanyanDBClient client) throws BanyanDBException {
        return client.findGroups().stream()
                     .filter(group -> !SERVER_GROUPS.contains(group.getMetadata().getName()))
                     .collect(Collectors.toList());
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
