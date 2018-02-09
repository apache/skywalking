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

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;

/**
 * @author peng-yongsheng
 */
public class GCMetricEsUIDAO extends EsDAO implements IGCMetricUIDAO {

    public GCMetricEsUIDAO(ElasticSearchClient client) {
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

        MultiGetRequestBuilder youngPrepareMultiGet = getClient().prepareMultiGet();
        durationPoints.forEach(durationPoint -> {
            String id = durationPoint.getPoint() + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + gcPhrase;
            youngPrepareMultiGet.add(tableName, GCMetricTable.TABLE_TYPE, id);
        });

        List<Integer> gcTrends = new LinkedList<>();
        MultiGetResponse multiGetResponse = youngPrepareMultiGet.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse.getResponses()) {
            if (itemResponse.getResponse().isExists()) {
                long count = ((Number)itemResponse.getResponse().getSource().get(GCMetricTable.COLUMN_COUNT)).longValue();
                long times = ((Number)itemResponse.getResponse().getSource().get(GCMetricTable.COLUMN_TIMES)).intValue();
                gcTrends.add((int)(count / times));
            } else {
                gcTrends.add(0);
            }
        }

        return gcTrends;
    }
}
