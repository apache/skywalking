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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricEsUIDAO extends EsDAO implements IMemoryPoolMetricUIDAO {

    public MemoryPoolMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket, int poolType) {
        String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
        GetResponse getResponse = getClient().prepareGet(MemoryPoolMetricTable.TABLE, id).get();

        JsonObject metric = new JsonObject();
        if (getResponse.isExists()) {
            metric.addProperty("max", ((Number)getResponse.getSource().get(MemoryPoolMetricTable.COLUMN_MAX)).intValue());
            metric.addProperty("init", ((Number)getResponse.getSource().get(MemoryPoolMetricTable.COLUMN_INIT)).intValue());
            metric.addProperty("used", ((Number)getResponse.getSource().get(MemoryPoolMetricTable.COLUMN_USED)).intValue());
        } else {
            metric.addProperty("max", 0);
            metric.addProperty("init", 0);
            metric.addProperty("used", 0);
        }
        return metric;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket, int poolType) {
        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet();

        long timeBucket = startTimeBucket;
        do {
//            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND, timeBucket, 1);
            String id = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + poolType;
            prepareMultiGet.add(MemoryPoolMetricTable.TABLE, MemoryPoolMetricTable.TABLE_TYPE, id);
        }
        while (timeBucket <= endTimeBucket);

        JsonObject metric = new JsonObject();
        JsonArray usedMetric = new JsonArray();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                metric.addProperty("max", ((Number)response.getResponse().getSource().get(MemoryPoolMetricTable.COLUMN_MAX)).longValue());
                metric.addProperty("init", ((Number)response.getResponse().getSource().get(MemoryPoolMetricTable.COLUMN_INIT)).longValue());
                usedMetric.add(((Number)response.getResponse().getSource().get(MemoryPoolMetricTable.COLUMN_USED)).longValue());
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
