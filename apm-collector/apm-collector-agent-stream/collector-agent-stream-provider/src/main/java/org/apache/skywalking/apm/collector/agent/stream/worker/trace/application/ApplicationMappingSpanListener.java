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


package org.apache.skywalking.apm.collector.agent.stream.worker.trace.application;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.apache.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationMappingSpanListener implements FirstSpanListener, EntrySpanListener {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingSpanListener.class);

    private List<ApplicationMapping> applicationMappings = new LinkedList<>();
    private long timeBucket;

    @Override public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        logger.debug("node mapping listener parse reference");
        if (spanDecorator.getRefsCount() > 0) {
            ApplicationMapping applicationMapping = new ApplicationMapping(Const.EMPTY_STRING);
            applicationMapping.setApplicationId(applicationId);
            applicationMapping.setAddressId(spanDecorator.getRefs(0).getNetworkAddressId());
            String id = String.valueOf(applicationId) + Const.ID_SPLIT + String.valueOf(applicationMapping.getAddressId());
            applicationMapping.setId(id);
            applicationMappings.add(applicationMapping);
        }
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        logger.debug("node mapping listener build");
        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.APPLICATION_MAPPING_GRAPH_ID, ApplicationMapping.class);
        applicationMappings.forEach(applicationMapping -> {
            applicationMapping.setId(timeBucket + Const.ID_SPLIT + applicationMapping.getId());
            applicationMapping.setTimeBucket(timeBucket);
            logger.debug("push to node mapping aggregation worker, id: {}", applicationMapping.getId());
            graph.start(applicationMapping);
        });
    }
}
