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

package org.apache.skywalking.apm.collector.analysis.metric.provider;

import java.util.Properties;
import org.apache.skywalking.apm.collector.analysis.metric.define.AnalysisMetricModule;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationComponentGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationComponentSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationMappingGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationMappingSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.ApplicationReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.GlobalTraceGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.GlobalTraceSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.InstanceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.InstanceReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment.SegmentCostGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment.SegmentCostSpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.ServiceEntryGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.ServiceEntrySpanListener;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.ServiceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.ServiceReferenceMetricGraph;
import org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.ServiceReferenceMetricSpanListener;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.AnalysisSegmentParserModule;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParserListenerRegister;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;

/**
 * @author peng-yongsheng
 */
public class AnalysisMetricModuleProvider extends ModuleProvider {

    public static final String NAME = "default";

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AnalysisMetricModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        segmentParserListenerRegister();

        ServiceReferenceMetricGraph serviceReferenceMetricGraph = new ServiceReferenceMetricGraph(getManager());
        serviceReferenceMetricGraph.create();

        InstanceReferenceMetricGraph instanceReferenceMetricGraph = new InstanceReferenceMetricGraph(getManager());
        instanceReferenceMetricGraph.create();

        ApplicationReferenceMetricGraph applicationReferenceMetricGraph = new ApplicationReferenceMetricGraph(getManager());
        applicationReferenceMetricGraph.create();

        ServiceMetricGraph serviceMetricGraph = new ServiceMetricGraph(getManager());
        serviceMetricGraph.create();

        InstanceMetricGraph instanceMetricGraph = new InstanceMetricGraph(getManager());
        instanceMetricGraph.create();

        ApplicationMetricGraph applicationMetricGraph = new ApplicationMetricGraph(getManager());
        applicationMetricGraph.create();

        ApplicationComponentGraph applicationComponentGraph = new ApplicationComponentGraph(getManager());
        applicationComponentGraph.create();

        ApplicationMappingGraph applicationMappingGraph = new ApplicationMappingGraph(getManager());
        applicationMappingGraph.create();

        ServiceEntryGraph serviceEntryGraph = new ServiceEntryGraph(getManager());
        serviceEntryGraph.create();

        GlobalTraceGraph globalTraceGraph = new GlobalTraceGraph(getManager());
        globalTraceGraph.create();

        SegmentCostGraph segmentCostGraph = new SegmentCostGraph(getManager());
        segmentCostGraph.create();
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }

    private void segmentParserListenerRegister() {
        ISegmentParserListenerRegister segmentParserListenerRegister = getManager().find(AnalysisSegmentParserModule.NAME).getService(ISegmentParserListenerRegister.class);
        segmentParserListenerRegister.register(new ServiceReferenceMetricSpanListener());
        segmentParserListenerRegister.register(new ApplicationComponentSpanListener());
        segmentParserListenerRegister.register(new ApplicationMappingSpanListener());
        segmentParserListenerRegister.register(new ServiceEntrySpanListener(getManager()));
        segmentParserListenerRegister.register(new GlobalTraceSpanListener());
        segmentParserListenerRegister.register(new SegmentCostSpanListener(getManager()));
    }
}
