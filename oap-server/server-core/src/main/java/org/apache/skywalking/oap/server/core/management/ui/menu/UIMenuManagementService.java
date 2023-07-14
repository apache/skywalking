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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class UIMenuManagementService implements Service, Runnable {
    private static final String MENU_ID = "1";
    private static final Gson GSON = new Gson();
    private static final int MENU_GET_MAX_SECOND = 3;

    private UIMenuManagementDAO menuDAO;
    private ModuleManager moduleManager;
    private CompletableFuture<List<MenuItem>> menuItemFuture;
    private boolean isMenuItemsBeenFetched = false;
    private MetadataQueryService metadataQueryService;

    public UIMenuManagementService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.menuItemFuture = new CompletableFuture<>();
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

    public void saveMenuAndStartFetch(List<UIMenuItemSetting> menuItems, int fetchInterval) throws IOException {
        // ignore if already existing
        if (getMenuDAO().getMenu(MENU_ID) != null) {
            startingFetchMenu(fetchInterval);
            return;
        }

        UIMenu menu = new UIMenu();
        menu.setMenuId(MENU_ID);
        menu.setUpdateTime(System.currentTimeMillis());
        menu.setConfigurationJson(GSON.toJson(menuItems));

        getMenuDAO().saveMenu(menu);
        startingFetchMenu(fetchInterval);
    }

    private void startingFetchMenu(int fetchIntervalSecond) {
        Executors.newSingleThreadScheduledExecutor()
            .scheduleWithFixedDelay(this, 0, fetchIntervalSecond, java.util.concurrent.TimeUnit.SECONDS);
    }

    public List<MenuItem> getMenuItems() {
        try {
            return menuItemFuture.get(MENU_GET_MAX_SECOND, TimeUnit.SECONDS);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to get menu items", t);
        }
    }

    @Override
    public void run() {
        try {
            UIMenu menu = getMenuDAO().getMenu(MENU_ID);
            if (menu == null) {
                log.warn("cannot find the menu data from storage");
                return;
            }

            List<UIMenuItemSetting> menuItems = GSON.fromJson(menu.getConfigurationJson(), new TypeToken<List<UIMenuItemSetting>>() {
            }.getType());
            final List<MenuItem> items = this.convertToMenuItems(menuItems);
            // if the menu haven't been fetched one time, then just complete it
            if (!isMenuItemsBeenFetched) {
                menuItemFuture.complete(items);
                isMenuItemsBeenFetched = true;
            } else {
                // otherwise, the value should be updated to new one
                menuItemFuture = CompletableFuture.completedFuture(items);
            }
        } catch (Throwable t) {
            log.warn("Failed to fetch menu items", t);
            menuItemFuture.completeExceptionally(t);
        }
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
