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

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Catalog;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.IntervalRule;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.ResourceOpts;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.LifecycleStage;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITGroupMetadataRegistryTest extends BanyanDBClientTestCI {
    @BeforeEach
    public void setUp() throws IOException, BanyanDBException, InterruptedException {
        super.setUpConnection();
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.closeClient();
    }

    @Test
    public void testGroupRegistry_createAndGet() throws BanyanDBException {
        Group expectedGroup = buildGroup();
        this.client.define(expectedGroup);
        Group actualGroup = client.findGroup("sw_metric");
        assertNotNull(actualGroup);
        assertNotNull(actualGroup.getUpdatedAt());
        actualGroup = actualGroup.toBuilder().setMetadata(actualGroup.getMetadata().toBuilder().clearModRevision().clearCreateRevision()).build();
        assertEquals(expectedGroup, actualGroup);
    }

    @Test
    public void testGroupRegistry_createAndUpdate() throws BanyanDBException {
        this.client.define(buildGroup());
        Group beforeGroup = client.findGroup("sw_metric");
        assertNotNull(beforeGroup);
        assertNotNull(beforeGroup.getUpdatedAt());
        Group updatedGroup = beforeGroup.toBuilder()
                                        .setResourceOpts(beforeGroup.getResourceOpts()
                                                                    .toBuilder()
                                                                    .setTtl(IntervalRule.newBuilder()
                                                                                        .setUnit(
                                                                                            IntervalRule.Unit.UNIT_DAY)
                                                                                        .setNum(3)))
                                        .build();
        this.client.update(updatedGroup);
        Group afterGroup = client.findGroup("sw_metric");
        updatedGroup = updatedGroup.toBuilder().setMetadata(updatedGroup.getMetadata().toBuilder().clearModRevision()).build();
        afterGroup = afterGroup.toBuilder().setMetadata(afterGroup.getMetadata().toBuilder().clearModRevision()).build();
        assertNotNull(afterGroup);
        assertNotNull(afterGroup.getUpdatedAt());
        assertEquals(updatedGroup, afterGroup);
    }

    @Test
    public void testGroupRegistry_createAndList() throws BanyanDBException {
        Group expectedGroup = buildGroup();
        this.client.define(buildGroup());
        List<Group> actualGroups = client.findGroups();
        assertNotNull(actualGroups);
        assertEquals(1, actualGroups.size());
        Group actualGroup = actualGroups.get(0);
        actualGroup = actualGroup.toBuilder().setMetadata(actualGroup.getMetadata().toBuilder().clearModRevision().clearCreateRevision()).build();
        assertEquals(expectedGroup, actualGroup);
    }

    @Test
    public void testGroupRegistry_createAndDelete() throws BanyanDBException {
        this.client.define(buildGroup());
        boolean deleted = this.client.deleteGroup("sw_metric");
        assertTrue(deleted);
        assertNull(client.findGroup("sw_metric"));
    }

    @Test
    public void testGroupRegistry_hotWarmCold() throws BanyanDBException {
        Group g = Group.newBuilder().setMetadata(Metadata.newBuilder().setName("sw_record"))
            .setCatalog(Catalog.CATALOG_STREAM)
            .setResourceOpts(ResourceOpts.newBuilder()
                .setShardNum(3)
                .setSegmentInterval(
                    IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(1))
                .setTtl(
                    IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(3))
                .addStages(LifecycleStage.newBuilder()
                    .setName("warm")
                    .setShardNum(2)
                    .setSegmentInterval(IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(1))
                    .setTtl(IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(7))
                    .setNodeSelector("hdd-nodes")
                    .build())
                .addStages(LifecycleStage.newBuilder()
                    .setName("cold")
                    .setShardNum(1)
                    .setSegmentInterval(IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(7))
                    .setTtl(IntervalRule.newBuilder()
                        .setUnit(IntervalRule.Unit.UNIT_DAY)
                        .setNum(30))
                    .setNodeSelector("archive-nodes")
                    .setClose(true)
                    .build()))
            .build();
        this.client.define(g);
        Group actualGroup = client.findGroup("sw_record");
        assertNotNull(actualGroup);
        assertNotNull(actualGroup.getUpdatedAt());

        // Verify group exists
        assertNotNull(actualGroup);

        // Verify basic metadata
        assertEquals("sw_record", actualGroup.getMetadata().getName());
        assertEquals(Catalog.CATALOG_STREAM, actualGroup.getCatalog());

        // Verify resource options
        ResourceOpts actualOpts = actualGroup.getResourceOpts();
        assertEquals(3, actualOpts.getShardNum());
        assertEquals(IntervalRule.Unit.UNIT_DAY, actualOpts.getSegmentInterval().getUnit());
        assertEquals(1, actualOpts.getSegmentInterval().getNum());
        assertEquals(IntervalRule.Unit.UNIT_DAY, actualOpts.getTtl().getUnit());
        assertEquals(3, actualOpts.getTtl().getNum());

        // Verify stages (should have 2 stages: warm and cold)
        assertEquals(2, actualOpts.getStagesCount());

        // Verify warm stage
        LifecycleStage warmStage = actualOpts.getStages(0);
        assertEquals("warm", warmStage.getName());
        assertEquals(2, warmStage.getShardNum());
        assertEquals(1, warmStage.getSegmentInterval().getNum());
        assertEquals(IntervalRule.Unit.UNIT_DAY, warmStage.getSegmentInterval().getUnit());
        assertEquals(7, warmStage.getTtl().getNum());
        assertEquals("hdd-nodes", warmStage.getNodeSelector());

        // Verify cold stage
        LifecycleStage coldStage = actualOpts.getStages(1);
        assertEquals("cold", coldStage.getName());
        assertEquals(1, coldStage.getShardNum());
        assertEquals(7, coldStage.getSegmentInterval().getNum());
        assertEquals(30, coldStage.getTtl().getNum());
        assertEquals("archive-nodes", coldStage.getNodeSelector());
        assertTrue(coldStage.getClose());
    }

    private Group buildGroup() {
        return Group.newBuilder().setMetadata(Metadata.newBuilder().setName("sw_metric"))
                    .setCatalog(Catalog.CATALOG_MEASURE)
                    .setResourceOpts(ResourceOpts.newBuilder()
                                                 .setShardNum(2)
                                                 .setSegmentInterval(
                                                     IntervalRule.newBuilder()
                                                                 .setUnit(
                                                                     IntervalRule.Unit.UNIT_DAY)
                                                                 .setNum(
                                                                     1))
                                                 .setTtl(
                                                     IntervalRule.newBuilder()
                                                                 .setUnit(
                                                                     IntervalRule.Unit.UNIT_DAY)
                                                                 .setNum(
                                                                     7)))
                    .build();
    }
}
