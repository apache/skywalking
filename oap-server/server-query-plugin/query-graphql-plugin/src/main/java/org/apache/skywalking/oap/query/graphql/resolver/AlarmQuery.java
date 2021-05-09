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

package org.apache.skywalking.oap.query.graphql.resolver;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.AlarmQueryService;
import org.apache.skywalking.oap.server.core.query.EventQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.AlarmTrend;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.event.Event;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.nonNull;

public class AlarmQuery implements GraphQLQueryResolver {
    private final ModuleManager moduleManager;
    private AlarmQueryService queryService;
    private EventQueryService eventQueryService;
    private ForkJoinPool forkJoinPool;

    public AlarmQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private AlarmQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(AlarmQueryService.class);
        }
        return queryService;
    }

    private EventQueryService getEventQueryService() {
        if (eventQueryService == null) {
            this.eventQueryService = moduleManager.find(CoreModule.NAME).provider().getService(EventQueryService.class);
        }
        return eventQueryService;
    }

    private ForkJoinPool getForkJoinPool() {
        if (forkJoinPool == null) {
            this.forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        }
        return forkJoinPool;
    }

    public AlarmTrend getAlarmTrend(final Duration duration) {
        return new AlarmTrend();
    }

    public Alarms getAlarm(final Duration duration, final Scope scope, final String keyword,
                           final Pagination paging, final List<Tag> tags) throws Throwable {
        Integer scopeId = null;
        if (scope != null) {
            scopeId = scope.getScopeId();
        }
        long startSecondTB = 0;
        long endSecondTB = 0;
        EventQueryCondition condition = new EventQueryCondition();
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
            condition.setTime(duration);
        }
        Alarms alarms = getQueryService().getAlarm(
                scopeId, keyword, paging, startSecondTB, endSecondTB, tags);
        return includeEvents2AlarmsByCondition(alarms, condition);
    }

    private Alarms includeEvents2AlarmsByCondition(Alarms alarms, EventQueryCondition condition) throws Throwable {
        if (alarms.getTotal() < 1) {
            return alarms;
        }
        SearchEventTask searchEventTask = new SearchEventTask(alarms.getMsgs(), condition);
        ForkJoinTask<List<AlarmMessage>> queryEventTask = getForkJoinPool().submit(searchEventTask);
        List<AlarmMessage> msgs = queryEventTask.get();
        if (queryEventTask.isCompletedAbnormally()) {
            Throwable exception = queryEventTask.getException();
            if (Objects.nonNull(exception)) {
                throw exception;
            } else {
                throw new RuntimeException("Unknown Exception In Current SearchEventTask!");
            }
        }
        if (CollectionUtils.isNotEmpty(msgs)) {
            Alarms results = new Alarms();
            results.getMsgs().addAll(msgs);
            results.setTotal(alarms.getTotal());
            return results;
        }
        return alarms;
    }

    /**
     * I/O intensive Task. Too many threads are waiting on I/0 processing.
     */
    class SearchEventTask extends RecursiveTask<List<AlarmMessage>> {
        private EventQueryCondition condition;
        private final Integer threshold = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        private List<AlarmMessage> msgs;

        SearchEventTask(List<AlarmMessage> msgs, EventQueryCondition condition) {
            this.msgs = msgs;
            this.condition = condition;
        }

        @SneakyThrows
        @Override
        protected List<AlarmMessage> compute() {
            int gap = msgs.size();
            if (gap <= threshold) {
                includeEvents2AlarmMsgs(this.msgs, gap, this.condition);
                return this.msgs;
            } else if (gap <= (threshold << 1)) {
                int mid = gap >> 1;
                SearchEventTask leftTask = new SearchEventTask(this.msgs.subList(0, mid), this.condition);
                SearchEventTask rightTask = new SearchEventTask(this.msgs.subList(mid, gap), this.condition);
                invokeAll(leftTask, rightTask);

                List<AlarmMessage> leftParts = leftTask.join();
                List<AlarmMessage> rightParts = rightTask.join();

                List<AlarmMessage> results = new ArrayList<>();
                results.addAll(leftParts);
                results.addAll(rightParts);
                return results;
            } else {
                int mid = gap >> 1;
                int leftMid = mid >> 1;
                int rightMid = mid + leftMid;

                SearchEventTask firstTask = new SearchEventTask(this.msgs.subList(0, leftMid), this.condition);
                SearchEventTask secondTask = new SearchEventTask(this.msgs.subList(leftMid, mid), this.condition);
                SearchEventTask thirdTask = new SearchEventTask(this.msgs.subList(mid, rightMid), this.condition);
                SearchEventTask fourthTask = new SearchEventTask(this.msgs.subList(rightMid, gap), this.condition);
                invokeAll(firstTask, secondTask, thirdTask, fourthTask);

                List<AlarmMessage> firstParts = firstTask.join();
                List<AlarmMessage> secondParts = secondTask.join();
                List<AlarmMessage> thirdParts = thirdTask.join();
                List<AlarmMessage> fourthParts = fourthTask.join();

                List<AlarmMessage> results = new ArrayList<>();
                results.addAll(firstParts);
                results.addAll(secondParts);
                results.addAll(thirdParts);
                results.addAll(fourthParts);
                return results;
            }
        }

        private void includeEvents2AlarmMsgs(List<AlarmMessage> msgs, int end, EventQueryCondition condition) throws Exception {
            for (int index = 0; index < end; index++) {
                AlarmMessage alarm = msgs.get(index);
                List<Source> sources = constructCurrentSource(alarm);
                List<Event> events = new ArrayList<>(sources.size());
                if (CollectionUtils.isNotEmpty(sources)) {
                    for (int i = 0; i < sources.size(); i++) {
                        condition.setSource(sources.get(i));
                        events.addAll(getEventQueryService().queryEvents(condition).getEvents());
                    }
                    msgs.get(index).setEvents(events);
                }
            }
        }

        private List<Source> constructCurrentSource(AlarmMessage msg) {
            List<Source> sources = new ArrayList<>(2);
            switch (msg.getScopeId()) {
                case DefaultScopeDefine.SERVICE :
                    IDManager.ServiceID.ServiceIDDefinition serviceIdDef = IDManager.ServiceID.analysisId(msg.getId());
                    Source serviceSource = new Source();
                    serviceSource.setService(serviceIdDef.getName());
                    sources.add(serviceSource);
                    break;
                case DefaultScopeDefine.SERVICE_RELATION :
                    IDManager.ServiceID.ServiceIDDefinition sourceServiceIdDef = IDManager.ServiceID.analysisId(msg.getId());
                    Source sourceSource = new Source();
                    sourceSource.setService(sourceServiceIdDef.getName());
                    sources.add(sourceSource);

                    IDManager.ServiceID.ServiceIDDefinition destServiceIdDef = IDManager.ServiceID.analysisId(msg.getId1());
                    Source destSource = new Source();
                    sourceSource.setService(destServiceIdDef.getName());
                    sources.add(destSource);
                    break;
                case DefaultScopeDefine.SERVICE_INSTANCE :
                    IDManager.ServiceInstanceID.InstanceIDDefinition instanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId());
                    Source serviceInstanceSource = new Source();
                    serviceInstanceSource.setServiceInstance(instanceIdDef.getName());
                    serviceInstanceSource.setService(IDManager.ServiceID.analysisId(instanceIdDef.getServiceId()).getName());
                    sources.add(serviceInstanceSource);
                    break;
                case DefaultScopeDefine.SERVICE_INSTANCE_RELATION :
                    IDManager.ServiceInstanceID.InstanceIDDefinition sourceInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId());
                    Source sourceServiceInstanceSource = new Source();
                    sourceServiceInstanceSource.setServiceInstance(sourceInstanceIdDef.getName());
                    sourceServiceInstanceSource.setService(IDManager.ServiceID.analysisId(sourceInstanceIdDef.getServiceId()).getName());
                    sources.add(sourceServiceInstanceSource);

                    IDManager.ServiceInstanceID.InstanceIDDefinition destInstanceIdDef = IDManager.ServiceInstanceID.analysisId(msg.getId1());
                    Source destServiceInstanceSource = new Source();
                    destServiceInstanceSource.setServiceInstance(destInstanceIdDef.getName());
                    destServiceInstanceSource.setService(IDManager.ServiceID.analysisId(destInstanceIdDef.getServiceId()).getName());
                    sources.add(destServiceInstanceSource);
                    break;
                case DefaultScopeDefine.ENDPOINT :
                    IDManager.EndpointID.EndpointIDDefinition endpointIDDef = IDManager.EndpointID.analysisId(msg.getId());
                    Source endpointSource = new Source();
                    endpointSource.setService(IDManager.ServiceID.analysisId(endpointIDDef.getServiceId()).getName());
                    sources.add(endpointSource);
                    break;
                case DefaultScopeDefine.ENDPOINT_RELATION :
                    IDManager.EndpointID.EndpointIDDefinition sourceEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId());
                    Source sourceEndpointSource = new Source();
                    sourceEndpointSource.setService(IDManager.ServiceID.analysisId(sourceEndpointIDDef.getServiceId()).getName());
                    sources.add(sourceEndpointSource);

                    IDManager.EndpointID.EndpointIDDefinition destEndpointIDDef = IDManager.EndpointID.analysisId(msg.getId1());
                    Source destEndpointSource = new Source();
                    destEndpointSource.setService(IDManager.ServiceID.analysisId(destEndpointIDDef.getServiceId()).getName());
                    sources.add(destEndpointSource);
                    break;
            }
            return sources;
        }
    }
}
