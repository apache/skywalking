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

import org.apache.skywalking.banyandb.model.v1.BanyandbModel.Tag;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel.TagValue;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel.Str;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Metadata;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagSpec;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TagType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class ITBanyanDBPropertyTests extends BanyanDBClientTestCI {
    @BeforeEach
    public void setUp() throws IOException, BanyanDBException, InterruptedException {
        super.setUpConnection();
        Group expectedGroup =
            Group.newBuilder().setMetadata(Metadata.newBuilder()
                            .setName("default"))
                    .setCatalog(BanyandbCommon.Catalog.CATALOG_PROPERTY)
                    .setResourceOpts(BanyandbCommon.ResourceOpts.newBuilder()
                            .setShardNum(2))
                    .build();
        client.define(expectedGroup);
        assertNotNull(expectedGroup);
        org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property expectedProperty =
            org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property.newBuilder()
                    .setMetadata(
                        Metadata.newBuilder()
                                .setGroup("default")
                                .setName("sw")
                                .build())
                     .addTags(
                        TagSpec.newBuilder()
                                .setName("name")
                                .setType(
                                        TagType.TAG_TYPE_STRING))
                    .build();
        client.define(expectedProperty);
        assertNotNull(expectedProperty);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.closeClient();
    }

    @Test
    public void test_PropertyCreateAndGet() throws BanyanDBException {
        Property property = buildProperty("default", "sw", "ui_template").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("hello")))).build();
        PropertyStore store = new PropertyStore(client.getChannel());
        assertTrue(store.apply(property).getCreated());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                            .addGroups("default")
                            .setName("sw")
                            .addIds("ui_template")
                    .build());
            assertEquals(1, resp.getPropertiesCount());
            Property gotProperty = resp.getProperties(0);
            assertNotNull(gotProperty);
            assertEquals(property.getTagsList(), gotProperty.getTagsList());
        });
    }

    @Test
    public void test_PropertyCreateDeleteAndGet() throws BanyanDBException {
        Property property = buildProperty("default", "sw", "ui_template").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("hello")))).build();
        PropertyStore store = new PropertyStore(client.getChannel());
        assertTrue(store.apply(property).getCreated());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                    .addGroups("default")
                    .setName("sw")
                    .addIds("ui_template")
                    .build());
            assertEquals(1, resp.getPropertiesCount());
            Property gotProperty = resp.getProperties(0);
            assertNotNull(gotProperty);
            assertEquals(property.getTagsList(), gotProperty.getTagsList());
        });
        BanyandbProperty.DeleteResponse result = store.delete("default", "sw", "ui_template");
        assertTrue(result.getDeleted());
        BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                .addGroups("default")
                .setName("sw")
                .addIds("ui_template")
                .build());
        assertEquals(0, resp.getPropertiesCount());
    }

    @Test
    public void test_PropertyCreateUpdateAndGet() throws BanyanDBException {
        Property property1 = buildProperty("default", "sw", "ui_template").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("hello")))).build();
        PropertyStore store = new PropertyStore(client.getChannel());
        assertTrue(store.apply(property1).getCreated());

        Property property2 = buildProperty("default", "sw", "ui_template").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("word")))).build();
        assertFalse(store.apply(property2).getCreated());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                    .addGroups("default")
                    .setName("sw")
                    .addIds("ui_template")
                    .build());
            assertEquals(1, resp.getPropertiesCount());
            Property gotProperty = resp.getProperties(0);
            assertNotNull(gotProperty);
            assertEquals(property2.getTagsList(), gotProperty.getTagsList());
        });
    }

    @Test
    public void test_PropertyList() throws BanyanDBException {
        Property property = buildProperty("default", "sw", "id1").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("bar")))).build();
        PropertyStore store = new PropertyStore(client.getChannel());
        assertTrue(store.apply(property).getCreated());
        property = buildProperty("default", "sw", "id2").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("foo")))).build();
        assertTrue(store.apply(property).getCreated());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            client.query(new PropertyQuery(Lists.newArrayList("default"), "sw", ImmutableSet.of("name")).build());
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                    .addGroups("default")
                    .setName("sw")
                    .build());
            assertEquals(2, resp.getPropertiesCount());
        });
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                    .addGroups("default")
                    .setName("sw")
                    .addIds("id1")
                    .addIds("id2")
                    .build());
            assertEquals(2, resp.getPropertiesCount());
        });
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(BanyandbProperty.QueryRequest.newBuilder()
                    .addGroups("default")
                    .setName("sw")
                    .addIds("id2")
                    .build());
            assertEquals(1, resp.getPropertiesCount());
        });
    }

    @Test
    public void test_PropertyQuery() throws BanyanDBException {
        Property property = buildProperty("default", "sw", "id1").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("bar")))).build();
        PropertyStore store = new PropertyStore(client.getChannel());
        assertTrue(store.apply(property).getCreated());
        property = buildProperty("default", "sw", "id2").toBuilder().addTags(
            Tag.newBuilder().setKey("name").setValue(
                TagValue.newBuilder().setStr(Str.newBuilder().setValue("foo")))).build();
        assertTrue(store.apply(property).getCreated());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            BanyandbProperty.QueryResponse resp = client.query(new PropertyQuery(Lists.newArrayList("default"), "sw", ImmutableSet.of("name")).build());
            assertEquals(2, resp.getPropertiesCount());
        });
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            PropertyQuery pQuery = new PropertyQuery(Lists.newArrayList("default"), "sw", ImmutableSet.of("name"));
            pQuery.criteria(PairQueryCondition.StringQueryCondition.eq("name", "foo"));
            BanyandbProperty.QueryResponse resp = client.query(pQuery.build());
            assertEquals(1, resp.getPropertiesCount());
        });
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            PropertyQuery pQuery = new PropertyQuery(Lists.newArrayList("default"), "sw", ImmutableSet.of("name"));
            pQuery.criteria(Or.create(PairQueryCondition.StringQueryCondition.eq("name", "foo"), 
            PairQueryCondition.StringQueryCondition.eq("name", "bar")));
            BanyandbProperty.QueryResponse resp = client.query(pQuery.build());
            assertEquals(2, resp.getPropertiesCount());
        }); 
    }

    private Property buildProperty(String group, String name, String id) {
        Property.Builder builder = Property.newBuilder()
                                                                             .setMetadata(
                                                                                        Metadata.newBuilder()
                                                                                                                .setGroup(
                                                                                                                    group)
                                                                                                                .setName(
                                                                                                                    name).build())
                                                                                    .setId(id);
        return builder.build();
    }
}
