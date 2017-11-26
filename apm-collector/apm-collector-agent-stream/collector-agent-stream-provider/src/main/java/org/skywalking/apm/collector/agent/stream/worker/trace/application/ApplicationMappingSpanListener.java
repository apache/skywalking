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

package org.skywalking.apm.collector.agent.stream.worker.trace.application;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.skywalking.apm.collector.agent.stream.parser.RefsListener;
import org.skywalking.apm.collector.agent.stream.parser.standardization.ReferenceDecorator;
import org.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationMappingSpanListener implements RefsListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMappingSpanListener.class);

    private List<ApplicationMapping> applicationMappings = new ArrayList<>();
    private long timeBucket;

    @Override public void parseRef(ReferenceDecorator referenceDecorator, int applicationId, int instanceId,
        String segmentId) {
        logger.debug("node mapping listener parse reference");
        ApplicationMapping applicationMapping = new ApplicationMapping(Const.EMPTY_STRING);
        applicationMapping.setApplicationId(applicationId);
        applicationMapping.setAddressId(referenceDecorator.getNetworkAddressId());
        String id = String.valueOf(applicationId) + Const.ID_SPLIT + String.valueOf(applicationMapping.getAddressId());
        applicationMapping.setId(id);
        applicationMappings.add(applicationMapping);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        logger.debug("node mapping listener build");
        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.NODE_MAPPING_GRAPH_ID, ApplicationMapping.class);

        for (ApplicationMapping applicationMapping : applicationMappings) {
            applicationMapping.setId(timeBucket + Const.ID_SPLIT + applicationMapping.getId());
            applicationMapping.setTimeBucket(timeBucket);
            logger.debug("push to node mapping aggregation worker, id: {}", applicationMapping.getId());
            graph.start(applicationMapping);
        }
    }
}
