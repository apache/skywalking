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
import java.util.*;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class SegmentEsPersistenceDAO extends AbstractPersistenceEsDAO<Segment> implements ISegmentPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, Segment> {

    public SegmentEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return SegmentTable.TABLE;
    }

    @Override protected Segment esDataToStreamData(Map<String, Object> source) {
        Segment segment = new Segment();
        segment.setDataBinary(Base64.getDecoder().decode((String)source.get(SegmentTable.DATA_BINARY.getName())));
        segment.setTimeBucket(((Number)source.get(SegmentTable.TIME_BUCKET.getName())).longValue());
        return segment;
    }

    @Override protected XContentBuilder esStreamDataToEsData(Segment streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(SegmentTable.DATA_BINARY.getName(), new String(Base64.getEncoder().encode(streamData.getDataBinary())))
            .field(SegmentTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return SegmentTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + SegmentTable.TABLE)
    @Override public Segment get(String id) {
        return super.get(id);
    }
}
