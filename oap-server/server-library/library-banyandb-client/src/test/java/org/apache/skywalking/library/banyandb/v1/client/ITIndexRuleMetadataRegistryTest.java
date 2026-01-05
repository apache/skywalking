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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.IndexRuleMetadataRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITIndexRuleMetadataRegistryTest extends BanyanDBClientTestCI {
    private IndexRuleMetadataRegistry registry;

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
    public void testIndexRuleRegistry_createAndGet() throws BanyanDBException {
        IndexRule indexRule = buildIndexRule();
        this.client.define(indexRule);
        IndexRule getIndexRule = this.client.findIndexRule("sw_record", "trace_id");
        assertNotNull(getIndexRule);
        getIndexRule = getIndexRule.toBuilder()
                                   .clearUpdatedAt()
                                   .setMetadata(getIndexRule.getMetadata()
                                                            .toBuilder()
                                                            .clearCreateRevision()
                                                            .clearModRevision()
                                                            .clearId())
                                   .build();
        assertEquals(indexRule, getIndexRule);
        assertNotNull(getIndexRule.getUpdatedAt());
    }

    @Test
    public void testIndexRuleRegistry_createAndList() throws BanyanDBException {
        IndexRule indexRule = buildIndexRule();
        this.client.define(indexRule);
        List<IndexRule> listIndexRule = this.client.findIndexRules("sw_record");
        assertNotNull(listIndexRule);
        assertEquals(1, listIndexRule.size());
        IndexRule actualIndexRule = listIndexRule.get(0);
        actualIndexRule = actualIndexRule.toBuilder()
                                   .clearUpdatedAt()
                                   .setMetadata(actualIndexRule.getMetadata()
                                                            .toBuilder()
                                                            .clearCreateRevision()
                                                            .clearModRevision()
                                                            .clearId())
                                   .build();
        assertEquals(actualIndexRule, indexRule);
    }

    @Test
    public void testIndexRuleRegistry_createAndUpdate() throws BanyanDBException {
        this.client.define(buildIndexRule());
        IndexRule before = client.findIndexRule("sw_record", "trace_id");
        assertEquals("simple", before.getAnalyzer());
        IndexRule updatedIndexRule = before.toBuilder().setAnalyzer("standard").build();
        this.client.update(updatedIndexRule);
        IndexRule after = this.client.findIndexRule("sw_record", "trace_id");
        assertNotNull(after);
        assertEquals("standard", after.getAnalyzer());
    }

    @Test
    public void testIndexRuleRegistry_createAndDelete() throws BanyanDBException {
        IndexRule indexRule = buildIndexRule();
        client.define(indexRule);
        boolean deleted = this.client.deleteIndexRule("sw_record", "trace_id");
        assertTrue(deleted);
        assertNull(client.findIndexRule(indexRule.getMetadata().getGroup(), indexRule.getMetadata().getName()));
    }

    private IndexRule buildIndexRule() {
        IndexRule.Builder builder = IndexRule.newBuilder()
                                             .setMetadata(Metadata.newBuilder()
                                                                  .setGroup("sw_record")
                                                                  .setName("trace_id"))
                                             .addTags("trace_id")
                                             .setType(IndexRule.Type.TYPE_INVERTED)
            .setAnalyzer("simple");
        return builder.build();
    }
}
