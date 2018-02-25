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
import org.apache.skywalking.apm.collector.storage.dao.ui.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.client.JestResult;
import io.searchbox.core.MultiGet;

/**
 * @author peng-yongsheng
 */
public class CpuMetricEsUIDAO extends EsHttpDAO implements ICpuMetricUIDAO {

    public CpuMetricEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public List<Integer> getCPUTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {

        String tableName = TimePyramidTableNameBuilder.build(step, CpuMetricTable.TABLE);

        MultiGet.Builder.ById multiGetBuilder = new MultiGet.Builder.ById(tableName, "type");
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId;
//            prepareMultiGet.add(tableName, CpuMetricTable.TABLE_TYPE, id);
            multiGetBuilder.addId(id);
        });

        List<Integer> cpuTrends = new LinkedList<>();
//        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        JestResult result = getClient().execute(multiGetBuilder.build());
        JsonArray docs =  result.getJsonObject().getAsJsonArray("docs");
        
        for (JsonElement response : docs) {
            JsonObject responseObj = response.getAsJsonObject();
            if (responseObj.get("found").getAsBoolean()) {
                JsonObject source = responseObj.getAsJsonObject("_source");
                double cpuUsed = (source.get(CpuMetricTable.COLUMN_USAGE_PERCENT)).getAsDouble();
                long times = (source.get(CpuMetricTable.COLUMN_TIMES).getAsLong());
                cpuTrends.add((int)((cpuUsed / times) * 100));
            } else {
                cpuTrends.add(0);
            }
        }
        return cpuTrends;
    }
}
