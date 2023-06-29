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
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.metadata.Property;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;

@Slf4j
public class BanyanDBUIMenuManagementDAO extends AbstractBanyanDBDAO implements UIMenuManagementDAO {
    public static final String GROUP = "sw";

    public BanyanDBUIMenuManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public UIMenu getMenu(String id) throws IOException {
        Property p = getClient().queryProperty(GROUP, UIMenu.INDEX_NAME, id);
        if (p == null) {
            return null;
        }
        return parse(p);
    }

    @Override
    public void saveMenu(UIMenu menu) throws IOException {
        this.getClient().define(Property.create(GROUP, UIMenu.INDEX_NAME, menu.id().build())
            .addTag(TagAndValue.newStringTag(UIMenu.CONFIGURATION, menu.getConfigurationJson()))
            .addTag(TagAndValue.newLongTag(UIMenu.UPDATE_TIME, menu.getUpdateTime()))
            .build());
    }

    public UIMenu parse(Property property) {
        UIMenu menu = new UIMenu();
        menu.setMenuId(property.id());

        for (TagAndValue<?> tagAndValue : property.tags()) {
            if (tagAndValue.getTagName().equals(UIMenu.CONFIGURATION)) {
                menu.setConfigurationJson((String) tagAndValue.getValue());
            } else if (tagAndValue.getTagName().equals(UIMenu.UPDATE_TIME)) {
                menu.setUpdateTime(((Number) tagAndValue.getValue()).longValue());
            }
        }
        return menu;
    }

}
