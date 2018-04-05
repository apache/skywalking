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

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.client.JestResult;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.MultiGet;

/**
 * @author cyberdak
 */
public class MemoryPoolMetricEsUIDAO extends EsHttpDAO implements IMemoryPoolMetricUIDAO {

    public MemoryPoolMetricEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket, int poolType) {
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
        DocumentResult getResponse = getClient().prepareGet(MemoryPoolMetricTable.TABLE, id);

        JsonObject metric = new JsonObject();
        if (getResponse.isSucceeded()) {
            JsonObject source = getResponse.getJsonObject().getAsJsonObject("_source");
            metric.addProperty("max", (source.get(MemoryPoolMetricTable.COLUMN_MAX)).getAsInt());
            metric.addProperty("init", (source.get(MemoryPoolMetricTable.COLUMN_INIT)).getAsInt());
            metric.addProperty("used", (source.get(MemoryPoolMetricTable.COLUMN_USED)).getAsInt());
        } else {
            metric.addProperty("max", 0);
            metric.addProperty("init", 0);
            metric.addProperty("used", 0);
        }
        return metric;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, int poolType) {
//        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();
        
        MultiGet.Builder.ById multiGet = new MultiGet.Builder.ById(MemoryPoolMetricTable.TABLE, MemoryPoolMetricTable.TABLE_TYPE);

        long timeBucket = startTimeBucket;
        do {
//            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND, timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
//            prepareMultiGet.add(MemoryPoolMetricTable.TABLE, MemoryPoolMetricTable.TABLE_TYPE, id);
            multiGet.addId(id);
        }
        while (timeBucket <= endTimeBucket);

        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();
        JestResult result = getClient().execute(multiGet.build());
        JsonArray multiGetResponse = result.getJsonObject().getAsJsonArray("docs");
        for (JsonElement response : multiGetResponse) {
            if (response.getAsJsonObject().get("found").getAsBoolean()) {
                JsonObject source = response.getAsJsonObject().getAsJsonObject("_source");
                metric.addProperty("max", (source.get(MemoryPoolMetricTable.COLUMN_MAX)).getAsLong());
                metric.addProperty("init", (source.get(MemoryPoolMetricTable.COLUMN_INIT)).getAsLong());
                usedMetric.add((source.get(MemoryPoolMetricTable.COLUMN_USED)).getAsLong());
            } else {
                metric.addProperty("max", 0);
                metric.addProperty("init", 0);
                usedMetric.add(0);
            }
        }
        metric.add("used", usedMetric);
        return metric;
    }
}
