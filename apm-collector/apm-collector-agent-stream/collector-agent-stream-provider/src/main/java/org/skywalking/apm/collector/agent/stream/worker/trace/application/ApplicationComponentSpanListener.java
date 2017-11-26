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
import org.skywalking.apm.collector.agent.stream.parser.EntrySpanListener;
import org.skywalking.apm.collector.agent.stream.parser.ExitSpanListener;
import org.skywalking.apm.collector.agent.stream.parser.FirstSpanListener;
import org.skywalking.apm.collector.agent.stream.parser.standardization.SpanDecorator;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentSpanListener implements EntrySpanListener, ExitSpanListener, FirstSpanListener {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentSpanListener.class);

    private List<ApplicationComponent> applicationComponents = new ArrayList<>();
    private long timeBucket;

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        ApplicationComponent applicationComponent = new ApplicationComponent(Const.EMPTY_STRING);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setPeerId(spanDecorator.getPeerId());

        String id = String.valueOf(applicationComponent.getComponentId()) + Const.ID_SPLIT + applicationComponent.getPeerId();

        applicationComponent.setId(id);
        applicationComponents.add(applicationComponent);
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        ApplicationComponent applicationComponent = new ApplicationComponent(Const.EMPTY_STRING);
        applicationComponent.setComponentId(spanDecorator.getComponentId());
        applicationComponent.setPeerId(applicationId);

        String id = String.valueOf(applicationComponent.getComponentId()) + Const.ID_SPLIT + String.valueOf(applicationId);
        applicationComponent.setId(id);

        applicationComponents.add(applicationComponent);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        timeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(spanDecorator.getStartTime());
    }

    @Override public void build() {
        Graph<ApplicationComponent> graph = GraphManager.INSTANCE.createIfAbsent(TraceStreamGraph.NODE_COMPONENT_GRAPH_ID, ApplicationComponent.class);

        applicationComponents.forEach(nodeComponent -> {
            nodeComponent.setId(timeBucket + Const.ID_SPLIT + nodeComponent.getId());
            nodeComponent.setTimeBucket(timeBucket);
            graph.start(nodeComponent);
        });
    }
}
