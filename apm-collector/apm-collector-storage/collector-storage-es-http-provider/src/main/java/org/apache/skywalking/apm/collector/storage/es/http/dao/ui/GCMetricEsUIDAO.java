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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.client.JestResult;
import io.searchbox.core.MultiGet;

/**
 * @author peng-yongsheng
 */
public class GCMetricEsUIDAO extends EsHttpDAO implements IGCMetricUIDAO {

    public GCMetricEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<Integer> getYoungGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.NEW_VALUE);
    }

    @Override public List<Integer> getOldGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        return getGCTrend(instanceId, step, durationPoints, GCPhrase.OLD_VALUE);
    }

    private List<Integer> getGCTrend(int instanceId, Step step, List<DurationPoint> durationPoints, int gcPhrase) {
        String tableName = TimePyramidTableNameBuilder.build(step, GCMetricTable.TABLE);

//        MultiGetRequestBuilder youngPrepareMultiGet = getClient().prepareMultiGet();
        
        MultiGet.Builder.ById multiGet = new MultiGet.Builder.ById(tableName, GCMetricTable.TABLE_TYPE);
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + gcPhrase;
//            youngPrepareMultiGet.add(tableName, GCMetricTable.TABLE_TYPE, id);
            multiGet.addId(id);
        });

        List<Integer> gcTrends = new LinkedList<>();
        
        JestResult result =  getClient().execute(multiGet.build());
        
        JsonArray multiGetResponse = result.getJsonObject().getAsJsonArray("docs");
        for (JsonElement itemResponse : multiGetResponse) {
            JsonObject itemResponseObj = itemResponse.getAsJsonObject();
            if (itemResponseObj.get("found").getAsBoolean()) {
                JsonObject source = itemResponseObj.getAsJsonObject("_source");
                long count = (source.get(GCMetricTable.COLUMN_COUNT).getAsLong());
                long times = (source.get(GCMetricTable.COLUMN_TIMES)).getAsLong();
                gcTrends.add((int)(count / times));
            } else {
                gcTrends.add(0);
            }
        }

        return gcTrends;
    }
}
