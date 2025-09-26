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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;

@Slf4j
public class BanyanDBUIMenuManagementDAO extends AbstractBanyanDBDAO implements UIMenuManagementDAO {

    public BanyanDBUIMenuManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public UIMenu getMenu(String id) throws IOException {
        Property p = getClient().queryProperty(UIMenu.INDEX_NAME, id);
        if (p == null) {
            return null;
        }
        return parse(p);
    }

    @Override
    public void saveMenu(UIMenu menu) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findManagementMetadata(UIMenu.INDEX_NAME);
        Property property = Property.newBuilder()
                                    .setMetadata(
                                        BanyandbCommon.Metadata.newBuilder().setGroup(schema.getMetadata().getGroup()).setName(UIMenu.INDEX_NAME))
                                    .setId(menu.getMenuId())
                                    .addTags(TagAndValue.newStringTag(UIMenu.CONFIGURATION, menu.getConfigurationJson())
                                                        .build())
                                    .addTags(TagAndValue.newLongTag(UIMenu.UPDATE_TIME, menu.getUpdateTime()).build())
                                    .build();
        this.getClient().apply(property);
    }

    public UIMenu parse(Property property) {
        UIMenu menu = new UIMenu();
        menu.setMenuId(property.getId());

        for (BanyandbModel.Tag tag : property.getTagsList()) {
            TagAndValue<?> tagAndValue = TagAndValue.fromProtobuf(tag);
            if (tagAndValue.getTagName().equals(UIMenu.CONFIGURATION)) {
                menu.setConfigurationJson((String) tagAndValue.getValue());
            } else if (tagAndValue.getTagName().equals(UIMenu.UPDATE_TIME)) {
                menu.setUpdateTime(((Number) tagAndValue.getValue()).longValue());
            }
        }
        return menu;
    }

}
