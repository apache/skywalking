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

import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.management.ui.menu.UIMenu;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;

public class UIMenuManagementEsDAO extends EsDAO implements UIMenuManagementDAO {
    public UIMenuManagementEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public UIMenu getMenu(int id) throws IOException {
        if (id <= 0) {
            return null;
        }
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(UIMenu.INDEX_NAME);
        final SearchBuilder search =
            Search.builder().query(Query.ids(String.valueOf(id)))
                .size(1);
        final SearchResponse response = getClient().search(index, search.build());

        if (response.getHits().getHits().size() > 0) {
            UIMenu.Builder builder = new UIMenu.Builder();
            SearchHit data = response.getHits().getHits().get(0);
            return builder.storage2Entity(new ElasticSearchConverter.ToEntity(UIMenu.INDEX_NAME, data.getSource()));
        }
        return null;
    }

    @Override
    public void saveMenu(UIMenu menu) throws IOException {
        try {
            final UIMenu.Builder builder = new UIMenu.Builder();
            final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(UIMenu.INDEX_NAME);
            builder.entity2Storage(menu, toStorage);
            getClient().forceInsert(UITemplate.INDEX_NAME, menu.id().build(), toStorage.obtain());
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
