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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRuleBinding;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.IndexRuleBindingMetadataRegistry;
import org.apache.skywalking.library.banyandb.v1.client.util.TimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient.DEFAULT_EXPIRE_AT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITIndexRuleBindingMetadataRegistryTest extends BanyanDBClientTestCI {
    private IndexRuleBindingMetadataRegistry registry;

    @BeforeEach
    public void setUp() throws IOException, BanyanDBException, InterruptedException {
        super.setUpConnection();
        BanyandbCommon.Group expectedGroup = buildStreamGroup();
        client.define(expectedGroup);
        assertNotNull(expectedGroup);
    }

    @AfterEach
    public void tearDown() throws IOException {
        this.closeClient();
    }

    @Test
    public void testIndexRuleBindingRegistry_createAndGet() throws BanyanDBException {
        IndexRuleBinding indexRuleBinding = buildIndexRuleBinding();
        this.client.define(indexRuleBinding);
        IndexRuleBinding getIndexRuleBinding = this.client.findIndexRuleBinding("sw_record", "trace_binding");
        assertNotNull(getIndexRuleBinding);
        getIndexRuleBinding = getIndexRuleBinding.toBuilder()
                                                 .clearUpdatedAt()
                                                 .setMetadata(getIndexRuleBinding.getMetadata()
                                                                                 .toBuilder()
                                                                                 .clearModRevision()
                                                                                 .clearCreateRevision())
                                                 .build();
        assertEquals(indexRuleBinding, getIndexRuleBinding);
        assertNotNull(getIndexRuleBinding.getUpdatedAt());
    }

    @Test
    public void testIndexRuleBindingRegistry_createAndList() throws BanyanDBException {
        IndexRuleBinding indexRuleBinding = buildIndexRuleBinding();
        this.client.define(indexRuleBinding);
        List<IndexRuleBinding> listIndexRuleBinding = this.client.findIndexRuleBindings("sw_record");
        assertNotNull(listIndexRuleBinding);
        IndexRuleBinding actualIndexRuleBinding = listIndexRuleBinding.get(0);
        actualIndexRuleBinding = actualIndexRuleBinding.toBuilder()
                                                 .clearUpdatedAt()
                                                 .setMetadata(actualIndexRuleBinding.getMetadata()
                                                                                 .toBuilder()
                                                                                 .clearModRevision()
                                                                                 .clearCreateRevision())
                                                 .build();
        assertEquals(1, listIndexRuleBinding.size());
        assertEquals(actualIndexRuleBinding, indexRuleBinding);
    }

    @Test
    public void testIndexRuleBindingRegistry_createAndDelete() throws BanyanDBException {
        IndexRuleBinding indexRuleBinding = buildIndexRuleBinding();
        this.client.define(indexRuleBinding);
        boolean deleted = this.client.deleteIndexRuleBinding("sw_record", "trace_binding");
        assertTrue(deleted);
        assertNull(client.findIndexRuleBinding(indexRuleBinding.getMetadata().getGroup(),
                                                      indexRuleBinding.getMetadata().getName()
        ));
    }

    private IndexRuleBinding buildIndexRuleBinding() {
        IndexRuleBinding.Builder builder = IndexRuleBinding.newBuilder()
                                                           .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                                               .setGroup("sw_record")
                                                                                               .setName("trace_binding"))
                                                           .setSubject(BanyandbDatabase.Subject.newBuilder()
                                                                                               .setCatalog(
                                                                                                   BanyandbCommon.Catalog.CATALOG_STREAM)
                                                                                               .setName("trace"))
                                                           .addAllRules(
                                                               Arrays.asList("trace_id"))
            .setBeginAt(TimeUtils.buildTimestamp(ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
            .setExpireAt(TimeUtils.buildTimestamp(DEFAULT_EXPIRE_AT));
        return builder.build();
    }
}
