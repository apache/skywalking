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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.library.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

/**
 * BanyanDB read / write / delete for {@link RuntimeRule}. Stored as a BanyanDB
 * {@code Property} — consistent with {@link BanyanDBUITemplateManagementDAO}. Writes go
 * through {@link #save(RuntimeRule)} and use {@code PropertyStore.apply}, which is upsert
 * by id. The generic {@code BanyanDBManagementDAO.insert} path is intentionally not used:
 * its body just logs and returns without persisting.
 */
@Slf4j
public class BanyanDBRuntimeRuleManagementDAO extends AbstractBanyanDBDAO implements RuntimeRuleManagementDAO {

    public BanyanDBRuntimeRuleManagementDAO(final BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<RuntimeRuleFile> getAll() throws IOException {
        final List<Property> properties = getClient().listProperties(RuntimeRule.INDEX_NAME);
        final List<RuntimeRuleFile> files = new ArrayList<>(properties.size());
        for (final Property p : properties) {
            files.add(parse(p));
        }
        return files;
    }

    @Override
    public void save(final RuntimeRule rule) throws IOException {
        final MetadataRegistry.Schema schema =
            MetadataRegistry.INSTANCE.findManagementMetadata(RuntimeRule.INDEX_NAME);
        if (schema == null) {
            throw new IOException(
                "BanyanDB schema for " + RuntimeRule.INDEX_NAME + " not registered yet");
        }
        // Property id matches RuntimeRule.id() (catalog + "_" + name) so apply() upserts on
        // the same composite key the read path keys off. Default apply strategy is MERGE,
        // but every save() call writes all five tags, so MERGE behaves like full replace.
        final Property property = Property.newBuilder()
            .setMetadata(BanyandbCommon.Metadata.newBuilder()
                .setGroup(schema.getMetadata().getGroup())
                .setName(RuntimeRule.INDEX_NAME))
            .setId(rule.id().build())
            .addTags(TagAndValue.newStringTag(RuntimeRule.CATALOG, rule.getCatalog()).build())
            .addTags(TagAndValue.newStringTag(RuntimeRule.NAME, rule.getName()).build())
            .addTags(TagAndValue.newStringTag(RuntimeRule.CONTENT, rule.getContent()).build())
            .addTags(TagAndValue.newStringTag(RuntimeRule.STATUS, rule.getStatus()).build())
            .addTags(TagAndValue.newLongTag(RuntimeRule.UPDATE_TIME, rule.getUpdateTime()).build())
            .build();
        getClient().apply(property);
    }

    @Override
    public void delete(final String catalog, final String name) throws IOException {
        // BanyanDB property id matches the StorageID composite built by RuntimeRule.id().
        // RuntimeRule.id() appends (CATALOG, NAME); StorageID.build() joins with the "_" separator.
        final String id = catalog + "_" + name;
        getClient().deleteProperty(RuntimeRule.INDEX_NAME, id);
    }

    private RuntimeRuleFile parse(final Property property) {
        String catalog = null;
        String name = null;
        String content = null;
        String status = null;
        long updateTime = 0L;
        for (final BanyandbModel.Tag tag : property.getTagsList()) {
            final TagAndValue<?> tv = TagAndValue.fromProtobuf(tag);
            final String tagName = tv.getTagName();
            final Object v = tv.getValue();
            if (RuntimeRule.CATALOG.equals(tagName)) {
                catalog = asString(v);
            } else if (RuntimeRule.NAME.equals(tagName)) {
                name = asString(v);
            } else if (RuntimeRule.CONTENT.equals(tagName)) {
                content = asString(v);
            } else if (RuntimeRule.STATUS.equals(tagName)) {
                status = asString(v);
            } else if (RuntimeRule.UPDATE_TIME.equals(tagName)) {
                updateTime = v == null ? 0L : ((Number) v).longValue();
            }
        }
        return new RuntimeRuleFile(catalog, name, content, status, updateTime);
    }

    private static String asString(final Object v) {
        return v == null ? null : v.toString();
    }
}
