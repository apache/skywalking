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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * Initialize the UI menu from files and starting fetch status after menu init finished.
 */
@Slf4j
public class UIMenuInitializer {
    private final UIMenuManagementService uiMenuManagementService;

    public UIMenuInitializer(ModuleManager manager) {
        this.uiMenuManagementService = manager.find(CoreModule.NAME)
            .provider()
            .getService(UIMenuManagementService.class);
    }

    public void init() throws IOException {
        final var menuFile = "ui-initialized-templates/menu.yaml";
        try {
            Reader menuReader = ResourceUtils.read(menuFile);
            Yaml yaml = new Yaml();
            final MenuData menuData = yaml.loadAs(menuReader, MenuData.class);
            if (menuData == null || CollectionUtils.isEmpty(menuData.getMenus())) {
                throw new IllegalArgumentException("cannot reading any menu items.");
            }

            // save menu and start fetch menu
            uiMenuManagementService.saveMenu(menuData.getMenus());
        } catch (FileNotFoundException e) {
            log.debug("No such file of path: {}, skipping loading UI menu.", menuFile);
        }
    }

    @Setter
    @Getter
    public static class MenuData {
        private List<UIMenuItemSetting> menus;
    }
}
