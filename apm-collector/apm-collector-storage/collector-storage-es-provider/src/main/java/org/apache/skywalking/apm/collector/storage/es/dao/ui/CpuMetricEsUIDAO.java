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

import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

import java.util.LinkedList;
import java.util.List;

/**
 * @author peng-yongsheng
 */
public class CpuMetricEsUIDAO extends EsDAO implements ICpuMetricUIDAO {

    public CpuMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<Integer> getCPUTrend(int instanceId, Step step, List<DurationPoint> durationPoints) {
        String tableName = TimePyramidTableNameBuilder.build(step, CpuMetricTable.TABLE);

        MultiGetRequestBuilder prepareMultiGet = getClient().prepareMultiGet(durationPoints, new ElasticSearchClient.MultiGetRowHandler<DurationPoint>() {
            @Override
            public void accept(DurationPoint durationPoint) {
                String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId;
                this.add(tableName, CpuMetricTable.TABLE_TYPE, id);
            }
        });


        List<Integer> cpuTrends = new LinkedList<>();
        MultiGetResponse multiGetResponse = prepareMultiGet.get();
        for (MultiGetItemResponse response : multiGetResponse.getResponses()) {
            if (response.getResponse().isExists()) {
                double cpuUsed = ((Number) response.getResponse().getSource().get(CpuMetricTable.USAGE_PERCENT.getName())).doubleValue();
                long times = ((Number) response.getResponse().getSource().get(CpuMetricTable.TIMES.getName())).longValue();
                cpuTrends.add((int) ((cpuUsed / times) * 100));
            } else {
                cpuTrends.add(0);
            }
        }
        return cpuTrends;
    }
}
