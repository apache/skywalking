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

package org.apache.skywalking.apm.collector.storage.es.dao;

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.global.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class GlobalTraceEsPersistenceDAO extends AbstractPersistenceEsDAO<GlobalTrace> implements IGlobalTracePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, GlobalTrace> {

    public GlobalTraceEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return GlobalTraceTable.TABLE;
    }

    @Override protected GlobalTrace esDataToStreamData(Map<String, Object> source) {
        GlobalTrace globalTrace = new GlobalTrace();
        globalTrace.setSegmentId((String)source.get(GlobalTraceTable.SEGMENT_ID.getName()));
        globalTrace.setTraceId((String)source.get(GlobalTraceTable.TRACE_ID.getName()));
        globalTrace.setTimeBucket(((Number)source.get(GlobalTraceTable.TIME_BUCKET.getName())).longValue());
        return globalTrace;
    }

    @Override protected XContentBuilder esStreamDataToEsData(GlobalTrace streamData) throws IOException {
        return XContentFactory.jsonBuilder()
            .startObject()
            .field(GlobalTraceTable.SEGMENT_ID.getName(), streamData.getSegmentId())
            .field(GlobalTraceTable.TRACE_ID.getName(), streamData.getTraceId())
            .field(GlobalTraceTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return GlobalTraceTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + GlobalTraceTable.TABLE)
    @Override public GlobalTrace get(String id) {
        return super.get(id);
    }
}
