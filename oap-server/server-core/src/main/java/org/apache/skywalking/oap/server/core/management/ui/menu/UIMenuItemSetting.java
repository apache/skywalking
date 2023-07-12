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

import lombok.Data;

import java.util.List;

@Data
public class UIMenuItemSetting {
    /**
     * Title of the menu item.
     */
    private String title;
    /**
     * Icon name of the menu item, it should only exist in the top level of menu.
     */
    private String icon;
    /**
     * The layer name of the menu item.
     */
    private String layer;
    /**
     * Sub menus of current item.
     */
    private List<UIMenuItemSetting> menus;
    /**
     * Description of the menu item.
     */
    private String description;
    /**
     * The document link for the latest version of this feature.
     */
    private String documentLink;
    /**
     * The i18n key for the title and description of this feature display in the UI.
     */
    private String i18nKey;
}
