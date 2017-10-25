/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;

/**
 * @author peng-yongsheng
 */
public class MemoryMetricEsDAO extends EsDAO implements IMemoryMetricDAO {

    @Override public JsonObject getMetric(int instanceId, long timeBucket, boolean isHeap) {
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
        GetResponse getResponse = getClient().prepareGet(MemoryMetricTable.TABLE, id).get();

        JsonObject metric = new JsonObject();
        if (getResponse.isExists()) {
            metric.addProperty("max", ((Number)getResponse.getSource().get(MemoryMetricTable.COLUMN_MAX)).intValue());
            metric.addProperty("init", ((Number)getResponse.getSource().get(MemoryMetricTable.COLUMN_INIT)).intValue());
            metric.addProperty("used", ((Number)getResponse.getSource().get(MemoryMetricTable.COLUMN_USED)).intValue());
        } else {
            metric.addProperty("max", 0);
            metric.addProperty("init", 0);
            metric.addProperty("used", 0);
        }
        return metric;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, boolean isHeap) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        int i = 0;
        long timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + isHeap;
            prepareMultiGet.add(MemoryMetricTable.TABLE, MemoryMetricTable.TABLE_TYPE, id);
        }
        while (timeBucket <= endTimeBucket);

        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                metric.addProperty("max", ((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_MAX)).longValue());
                metric.addProperty("init", ((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_INIT)).longValue());
                usedMetric.add(((Number)response.getResponse().getSource().get(MemoryMetricTable.COLUMN_USED)).longValue());
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
