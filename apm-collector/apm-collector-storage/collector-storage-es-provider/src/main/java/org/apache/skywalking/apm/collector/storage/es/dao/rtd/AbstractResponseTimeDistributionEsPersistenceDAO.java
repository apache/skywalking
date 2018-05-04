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

package org.apache.skywalking.apm.collector.storage.es.dao.rtd;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractResponseTimeDistributionEsPersistenceDAO extends AbstractPersistenceEsDAO<ResponseTimeDistribution> {

    AbstractResponseTimeDistributionEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ResponseTimeDistributionTable.TIME_BUCKET.getName();
    }

    @Override protected final ResponseTimeDistribution esDataToStreamData(Map<String, Object> source) {
        ResponseTimeDistribution responseTimeDistribution = new ResponseTimeDistribution();
        responseTimeDistribution.setMetricId((String)source.get(ResponseTimeDistributionTable.METRIC_ID.getName()));

        responseTimeDistribution.setStep(((Number)source.get(ResponseTimeDistributionTable.STEP.getName())).intValue());

        responseTimeDistribution.setCalls(((Number)source.get(ResponseTimeDistributionTable.CALLS.getName())).longValue());
        responseTimeDistribution.setErrorCalls(((Number)source.get(ResponseTimeDistributionTable.ERROR_CALLS.getName())).longValue());
        responseTimeDistribution.setSuccessCalls(((Number)source.get(ResponseTimeDistributionTable.SUCCESS_CALLS.getName())).longValue());

        responseTimeDistribution.setTimeBucket(((Number)source.get(ResponseTimeDistributionTable.TIME_BUCKET.getName())).longValue());
        return responseTimeDistribution;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ResponseTimeDistribution streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ResponseTimeDistributionTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(ResponseTimeDistributionTable.STEP.getName(), streamData.getStep());

        target.put(ResponseTimeDistributionTable.CALLS.getName(), streamData.getCalls());
        target.put(ResponseTimeDistributionTable.ERROR_CALLS.getName(), streamData.getErrorCalls());
        target.put(ResponseTimeDistributionTable.SUCCESS_CALLS.getName(), streamData.getSuccessCalls());

        target.put(ResponseTimeDistributionTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ResponseTimeDistributionTable.TABLE)
    @Override public final ResponseTimeDistribution get(String id) {
        return super.get(id);
    }
}
