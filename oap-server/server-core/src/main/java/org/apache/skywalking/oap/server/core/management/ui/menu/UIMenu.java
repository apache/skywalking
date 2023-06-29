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

package org.apache.skywalking.oap.server.core.management.ui.menu;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.UI_MENU;

@Setter
@Getter
@ScopeDeclaration(id = UI_MENU, name = "UIMenu")
@Stream(name = UIMenu.INDEX_NAME, scopeId = UI_MENU, builder = UIMenu.Builder.class, processor = ManagementStreamProcessor.class)
@EqualsAndHashCode(of = "menuId", callSuper = false)
public class UIMenu extends ManagementData {
    public static final String INDEX_NAME = "ui_menu";
    public static final String MENU_ID = "menu_id";
    public static final String CONFIGURATION = "configuration";
    public static final String UPDATE_TIME = "update_time";

    @Column(name = MENU_ID)
    private String menuId;
    @Column(name = CONFIGURATION, storageOnly = true, length = 1_000_000)
    private String configurationJson;
    @Column(name = UPDATE_TIME)
    private long updateTime;

    @Override
    public StorageID id() {
        return new StorageID().append(MENU_ID, menuId);
    }

    public static class Builder implements StorageBuilder<UIMenu> {

        @Override
        public UIMenu storage2Entity(Convert2Entity converter) {
            final UIMenu menu = new UIMenu();
            menu.setMenuId((String) converter.get(MENU_ID));
            menu.setConfigurationJson((String) converter.get(CONFIGURATION));
            menu.setUpdateTime(((Number) converter.get(UPDATE_TIME)).longValue());
            return menu;
        }

        @Override
        public void entity2Storage(UIMenu entity, Convert2Storage converter) {
            converter.accept(MENU_ID, entity.getMenuId());
            converter.accept(CONFIGURATION, entity.getConfigurationJson());
            converter.accept(UPDATE_TIME, entity.getUpdateTime());
        }
    }
}
