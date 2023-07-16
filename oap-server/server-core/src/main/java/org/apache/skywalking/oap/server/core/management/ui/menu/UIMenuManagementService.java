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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.type.MenuItem;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class UIMenuManagementService implements Service {
    private static final String MENU_ID = "1";
    private static final Gson GSON = new Gson();

    private final LoadingCache<Boolean, List<MenuItem>> menuItemCache;
    private UIMenuManagementDAO menuDAO;
    private ModuleManager moduleManager;
    private MetadataQueryService metadataQueryService;

    public UIMenuManagementService(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.menuItemCache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .refreshAfterWrite(moduleConfig.getUiMenuRefreshInterval(), TimeUnit.SECONDS)
            .build(new CacheLoader<>() {
                @Override
                public List<MenuItem> load(Boolean key) throws Exception {
                    return fetchMenuItems();
                }
            });
    }

    private UIMenuManagementDAO getMenuDAO() {
        if (menuDAO == null) {
            menuDAO = moduleManager.find(StorageModule.NAME).provider().getService(UIMenuManagementDAO.class);
        }
        return menuDAO;
    }

    private MetadataQueryService getMetadataQueryService() {
        if (metadataQueryService == null) {
            metadataQueryService = moduleManager.find(CoreModule.NAME).provider().getService(MetadataQueryService.class);
        }
        return metadataQueryService;
    }

    public void saveMenu(List<UIMenuItemSetting> menuItems) throws IOException {
        // ignore if already existing
        if (getMenuDAO().getMenu(MENU_ID) != null) {
            this.menuItemCache.get(true);
            return;
        }

        UIMenu menu = new UIMenu();
        menu.setMenuId(MENU_ID);
        menu.setUpdateTime(System.currentTimeMillis());
        menu.setConfigurationJson(GSON.toJson(menuItems));

        getMenuDAO().saveMenu(menu);
        this.menuItemCache.get(true);
    }

    @SneakyThrows
    public List<MenuItem> getMenuItems() {
        return menuItemCache.get(true);
    }

    private List<MenuItem> fetchMenuItems() throws IOException {
        UIMenu menu = getMenuDAO().getMenu(MENU_ID);
        if (menu == null) {
            throw new IllegalStateException("cannot found UI menu");
        }

        List<UIMenuItemSetting> menuItems = GSON.fromJson(menu.getConfigurationJson(), new TypeToken<List<UIMenuItemSetting>>() {
        }.getType());
        return this.convertToMenuItems(menuItems);
    }

    private List<MenuItem> convertToMenuItems(List<UIMenuItemSetting> settings) throws IOException {
        List<MenuItem> items = new ArrayList<>();
        for (UIMenuItemSetting setting : settings) {
            final MenuItem item = new MenuItem();
            boolean shouldActivate = true;
            if (CollectionUtils.isNotEmpty(setting.getMenus())) {
                // check should activate by sub items
                List<MenuItem> subItems = this.convertToMenuItems(setting.getMenus());
                shouldActivate = subItems.stream().anyMatch(MenuItem::isActivate);
                item.setSubItems(subItems);
            } else if (StringUtil.isNotEmpty(setting.getLayer())) {
                // check should active by dashboard
                shouldActivate = CollectionUtils.isNotEmpty(getMetadataQueryService().listServices(setting.getLayer(), null));
            }

            item.setTitle(setting.getTitle());
            item.setIcon(setting.getIcon());
            item.setLayer(StringUtil.isEmpty(setting.getLayer()) ? "" : setting.getLayer());
            item.setDescription(setting.getDescription());
            item.setDocumentLink(setting.getDocumentLink());
            item.setI18nKey(setting.getI18nKey());
            item.setActivate(shouldActivate);
            if (CollectionUtils.isEmpty(item.getSubItems())) {
                item.setSubItems(Collections.emptyList());
            }
            items.add(item);
        }
        return items;
    }

}
