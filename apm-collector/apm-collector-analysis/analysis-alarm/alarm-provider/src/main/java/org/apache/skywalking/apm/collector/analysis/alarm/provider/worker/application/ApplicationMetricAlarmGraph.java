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

package org.apache.skywalking.apm.collector.analysis.alarm.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.alarm.define.graph.AlarmWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAlarmGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public ApplicationMetricAlarmGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationMetric> graph = GraphManager.INSTANCE.createIfAbsent(AlarmGraphIdDefine.APPLICATION_METRIC_ALARM_GRAPH_ID, ApplicationMetric.class);

        graph.addNode(new ApplicationMetricAlarmAssertWorker.Factory(moduleManager).create(workerCreateListener))
            .addNext(new ApplicationMetricAlarmRemoteWorker.Factory(moduleManager, remoteSenderService, AlarmGraphIdDefine.APPLICATION_METRIC_ALARM_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationMetricAlarmPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        graph.toFinder().findNode(AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_REMOTE_WORKER_ID, ApplicationAlarm.class)
            .addNext(new ApplicationMetricAlarmToListNodeProcessor())
            .addNext(new ApplicationMetricAlarmListMinutePersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        graph.toFinder().findNode(AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_LIST_MINUTE_PERSISTENCE_WORKER_ID, ApplicationAlarmList.class)
            .addNext(new ApplicationMetricAlarmListHourTransformNode())
            .addNext(new ApplicationMetricAlarmListHourPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        graph.toFinder().findNode(AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_LIST_MINUTE_PERSISTENCE_WORKER_ID, ApplicationAlarmList.class)
            .addNext(new ApplicationMetricAlarmListDayTransformNode())
            .addNext(new ApplicationMetricAlarmListDayPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        graph.toFinder().findNode(AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_LIST_MINUTE_PERSISTENCE_WORKER_ID, ApplicationAlarmList.class)
            .addNext(new ApplicationMetricAlarmListMonthTransformNode())
            .addNext(new ApplicationMetricAlarmListMonthPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        link(graph);
    }

    private void link(Graph<ApplicationMetric> graph) {
        GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.APPLICATION_METRIC_GRAPH_ID, ApplicationMetric.class)
            .toFinder().findNode(MetricWorkerIdDefine.APPLICATION_MINUTE_METRIC_PERSISTENCE_WORKER_ID, ApplicationMetric.class)
            .addNext(new NodeProcessor<ApplicationMetric, ApplicationMetric>() {
                @Override public int id() {
                    return AlarmWorkerIdDefine.APPLICATION_METRIC_ALARM_GRAPH_BRIDGE_WORKER_ID;
                }

                @Override public void process(ApplicationMetric instanceMetric,
                    Next<ApplicationMetric> next) {
                    graph.start(instanceMetric);
                }
            });
    }
}
