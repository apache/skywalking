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
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.CompressionMethod;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.EncodingMethod;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Entity;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.FieldType;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.ShardingKey;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagFamilySpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagType;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ITMeasureMetadataRegistryTest extends BanyanDBClientTestCI {
    @BeforeEach
    public void setUp() throws IOException, BanyanDBException, InterruptedException {
        super.setUpConnection();
        BanyandbCommon.Group expectedGroup = buildMeasureGroup();
        client.define(expectedGroup);
        assertNotNull(expectedGroup);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.closeClient();
    }

    @Test
    public void testMeasureRegistry_createAndGet() throws BanyanDBException {
        Measure expectedMeasure = buildMeasure();
        this.client.define(expectedMeasure);
        Measure actualMeasure = client.findMeasure("sw_metric", "service_cpm_minute");
        assertNotNull(actualMeasure);
        assertNotNull(actualMeasure.getUpdatedAt());
        actualMeasure = actualMeasure.toBuilder().clearUpdatedAt().setMetadata(actualMeasure.getMetadata().toBuilder().clearModRevision().clearCreateRevision()).build();
        assertEquals(expectedMeasure, actualMeasure);
    }

    @Test
    public void testMeasureRegistry_createAndList() throws BanyanDBException {
        Measure expectedMeasure = buildMeasure();
        this.client.define(expectedMeasure);
        List<Measure> actualMeasures = client.findMeasures("sw_metric");
        assertNotNull(actualMeasures);
        // _topn_result is a system measure, so there should be 2 measures
        assertTrue(actualMeasures.size() > 0);
        boolean found = false;
        for (Measure actualMeasure : actualMeasures) {
            if (actualMeasure.getMetadata().getName().equals("service_cpm_minute")) {
                found = true;
                actualMeasure = actualMeasure.toBuilder().clearUpdatedAt().setMetadata(actualMeasure.getMetadata().toBuilder().clearModRevision().clearCreateRevision()).build();
                assertEquals(expectedMeasure, actualMeasure);
            }
        }
        if (!found) {
            fail("service_cpm_minute not found");
        }
    }

    @Test
    public void testMeasureRegistry_createAndDelete() throws BanyanDBException {
        Measure expectedMeasure = buildMeasure();
        this.client.define(expectedMeasure);
        boolean deleted = this.client.deleteMeasure(
            expectedMeasure.getMetadata().getGroup(), expectedMeasure.getMetadata().getName());
        assertTrue(deleted);
        assertNull(
            client.findMeasure(expectedMeasure.getMetadata().getGroup(), expectedMeasure.getMetadata().getName()));
    }

    private Measure buildMeasure() {
        Measure.Builder builder = Measure.newBuilder()
                                         .setMetadata(Metadata.newBuilder()
                                                              .setGroup("sw_metric")
                                                              .setName("service_cpm_minute"))
                                         .setInterval(Duration.ofMinutes(1).format())
                                         .setEntity(Entity.newBuilder().addTagNames("entity_id"))
                                         .setShardingKey(ShardingKey.newBuilder().addTagNames("service_id"))
                                         .addTagFamilies(
                                             TagFamilySpec.newBuilder()
                                                          .setName("default")
                                                          .addTags(
                                                              TagSpec.newBuilder()
                                                                     .setName("entity_id")
                                                                     .setType(
                                                                         TagType.TAG_TYPE_STRING))
                                                          .addTags(
                                                              TagSpec.newBuilder()
                                                                     .setName("scope")
                                                                     .setType(
                                                                         TagType.TAG_TYPE_STRING)))
                                         .addFields(
                                             FieldSpec.newBuilder()
                                                      .setName("total")
                                                      .setFieldType(
                                                          FieldType.FIELD_TYPE_INT)
                                                      .setCompressionMethod(
                                                          CompressionMethod.COMPRESSION_METHOD_ZSTD)
                                                      .setEncodingMethod(
                                                          EncodingMethod.ENCODING_METHOD_GORILLA))
                                         .addFields(
                                             FieldSpec.newBuilder()
                                                      .setName("value")
                                                      .setFieldType(
                                                          FieldType.FIELD_TYPE_INT)
                                                      .setCompressionMethod(
                                                          CompressionMethod.COMPRESSION_METHOD_ZSTD)
                                                      .setEncodingMethod(
                                                          EncodingMethod.ENCODING_METHOD_GORILLA));
        return builder.build();
    }

    @Test
    public void testIndexMeasureRegistry_createAndGet() throws BanyanDBException {
        Measure expectedMeasure = buildIndexMeasure();
        this.client.define(expectedMeasure);
        Measure actualMeasure = client.findMeasure("sw_metric", "service_traffic");
        assertNotNull(actualMeasure);
        assertNotNull(actualMeasure.getUpdatedAt());
        actualMeasure = actualMeasure.toBuilder().clearUpdatedAt().setMetadata(actualMeasure.getMetadata().toBuilder().clearModRevision().clearCreateRevision()).build();
        assertEquals(expectedMeasure, actualMeasure);
    }

    private Measure buildIndexMeasure() {
        Measure.Builder builder = Measure.newBuilder()
                .setMetadata(Metadata.newBuilder()
                        .setGroup("sw_metric")
                        .setName("service_traffic"))
                .setEntity(Entity.newBuilder().addTagNames("id"))
                .setIndexMode(true)
                .addTagFamilies(
                        TagFamilySpec.newBuilder()
                                .setName("default")
                                .addTags(
                                        TagSpec.newBuilder()
                                                .setName("id")
                                                .setType(
                                                        TagType.TAG_TYPE_STRING))
                                .addTags(
                                        TagSpec.newBuilder()
                                                .setName("service_name")
                                                .setType(
                                                        TagType.TAG_TYPE_STRING)));
        return builder.build();
    }
}
