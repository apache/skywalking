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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;

import java.io.IOException;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ManagementCRUDEsDAO;

public class UIMenuManagementEsDAO extends ManagementCRUDEsDAO implements UIMenuManagementDAO {
    public UIMenuManagementEsDAO(ElasticSearchClient client, StorageBuilder storageBuilder) {
        super(client, storageBuilder);
    }

    @Override
    public UIMenu getMenu(String id) throws IOException {
        return (UIMenu) super.getById(UIMenu.INDEX_NAME, id);
    }

    @Override
    public void saveMenu(UIMenu menu) throws IOException {
        super.create(UIMenu.INDEX_NAME, menu);
    }
}
