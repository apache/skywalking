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

package org.apache.skywalking.oap.server.core.query;

import com.google.common.base.Objects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v2.SegmentObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.query.entity.BasicTrace;
import org.apache.skywalking.oap.server.core.query.entity.KeyValue;
import org.apache.skywalking.oap.server.core.query.entity.LogEntity;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.entity.ProfiledSegment;
import org.apache.skywalking.oap.server.core.query.entity.ProfiledSpan;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static java.util.Objects.isNull;

/**
 * handle profile task queries
 */
public class ProfileTaskQueryService implements Service {
    private final ModuleManager moduleManager;
    private IProfileTaskQueryDAO profileTaskQueryDAO;
    private IProfileTaskLogQueryDAO profileTaskLogQueryDAO;
    private IProfileThreadSnapshotQueryDAO profileThreadSnapshotQueryDAO;
    private ServiceInventoryCache serviceInventoryCache;
    private ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private NetworkAddressInventoryCache networkAddressInventoryCache;
    private IComponentLibraryCatalogService componentLibraryCatalogService;

    private final ProfileAnalyzer profileAnalyzer;

    public ProfileTaskQueryService(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.profileAnalyzer = new ProfileAnalyzer(
            moduleManager, moduleConfig.getMaxPageSizeOfQueryProfileSnapshot(),
            moduleConfig.getMaxSizeOfAnalyzeProfileSnapshot()
        );
    }

    private IProfileTaskQueryDAO getProfileTaskDAO() {
        if (isNull(profileTaskQueryDAO)) {
            this.profileTaskQueryDAO = moduleManager.find(StorageModule.NAME)
                                                    .provider()
                                                    .getService(IProfileTaskQueryDAO.class);
        }
        return profileTaskQueryDAO;
    }

    private ServiceInventoryCache getServiceInventoryCache() {
        if (isNull(serviceInventoryCache)) {
            this.serviceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                      .provider()
                                                      .getService(ServiceInventoryCache.class);
        }
        return serviceInventoryCache;
    }

    private IProfileTaskLogQueryDAO getProfileTaskLogQueryDAO() {
        if (isNull(profileTaskLogQueryDAO)) {
            profileTaskLogQueryDAO = moduleManager.find(StorageModule.NAME)
                                                  .provider()
                                                  .getService(IProfileTaskLogQueryDAO.class);
        }
        return profileTaskLogQueryDAO;
    }

    private ServiceInstanceInventoryCache getServiceInstanceInventoryCache() {
        if (isNull(serviceInstanceInventoryCache)) {
            serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(ServiceInstanceInventoryCache.class);
        }
        return serviceInstanceInventoryCache;
    }

    private IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
        if (isNull(profileThreadSnapshotQueryDAO)) {
            profileThreadSnapshotQueryDAO = moduleManager.find(StorageModule.NAME)
                                                         .provider()
                                                         .getService(IProfileThreadSnapshotQueryDAO.class);
        }
        return profileThreadSnapshotQueryDAO;
    }

    private NetworkAddressInventoryCache getNetworkAddressInventoryCache() {
        if (networkAddressInventoryCache == null) {
            this.networkAddressInventoryCache = moduleManager.find(CoreModule.NAME)
                                                             .provider()
                                                             .getService(NetworkAddressInventoryCache.class);
        }
        return networkAddressInventoryCache;
    }

    private IComponentLibraryCatalogService getComponentLibraryCatalogService() {
        if (componentLibraryCatalogService == null) {
            this.componentLibraryCatalogService = moduleManager.find(CoreModule.NAME)
                                                               .provider()
                                                               .getService(IComponentLibraryCatalogService.class);
        }
        return componentLibraryCatalogService;
    }

    /**
     * search profile task list
     *
     * @param serviceId    monitor service
     * @param endpointName endpoint name to monitored
     */
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName) throws IOException {
        final List<ProfileTask> tasks = getProfileTaskDAO().getTaskList(serviceId, endpointName, null, null, null);

        // query all and filter on task to match logs
        List<ProfileTaskLog> taskLogList = getProfileTaskLogQueryDAO().getTaskLogList();
        if (taskLogList == null) {
            taskLogList = Collections.emptyList();
        }

        // add service name
        if (CollectionUtils.isNotEmpty(tasks)) {
            final ServiceInventoryCache serviceInventoryCache = getServiceInventoryCache();
            final ServiceInstanceInventoryCache serviceInstanceInventoryCache = getServiceInstanceInventoryCache();
            for (ProfileTask task : tasks) {
                final ServiceInventory serviceInventory = serviceInventoryCache.get(task.getServiceId());
                if (serviceInventory != null) {
                    task.setServiceName(serviceInventory.getName());
                }

                // filter all task logs
                task.setLogs(taskLogList.stream().filter(l -> Objects.equal(l.getTaskId(), task.getId())).map(l -> {
                    // get instance name from cache
                    final ServiceInstanceInventory instanceInventory = serviceInstanceInventoryCache.get(
                        l.getInstanceId());
                    if (instanceInventory != null) {
                        l.setInstanceName(instanceInventory.getName());
                    }
                    return l;
                }).collect(Collectors.toList()));
            }
        }

        return tasks;
    }

    /**
     * search profiled traces
     */
    public List<BasicTrace> getTaskTraces(String taskId) throws IOException {
        return getProfileThreadSnapshotQueryDAO().queryProfiledSegments(taskId);
    }

    public ProfileAnalyzation getProfileAnalyze(final String segmentId,
                                                final List<ProfileAnalyzeTimeRange> timeRanges) throws IOException {
        return profileAnalyzer.analyze(segmentId, timeRanges);
    }

    public ProfiledSegment getProfiledSegment(String segmentId) throws IOException {
        SegmentRecord segmentRecord = getProfileThreadSnapshotQueryDAO().getProfiledSegment(segmentId);
        if (segmentRecord == null) {
            return null;
        }

        ProfiledSegment profiledSegment = new ProfiledSegment();
        SegmentObject segmentObject = SegmentObject.parseFrom(segmentRecord.getDataBinary());
        profiledSegment.getSpans().addAll(buildProfiledSpanList(segmentObject));

        return profiledSegment;
    }

    private List<ProfiledSpan> buildProfiledSpanList(SegmentObject segmentObject) {
        List<ProfiledSpan> spans = new ArrayList<>();

        segmentObject.getSpansList().forEach(spanObject -> {
            ProfiledSpan span = new ProfiledSpan();
            span.setSpanId(spanObject.getSpanId());
            span.setParentSpanId(spanObject.getParentSpanId());
            span.setStartTime(spanObject.getStartTime());
            span.setEndTime(spanObject.getEndTime());
            span.setError(spanObject.getIsError());
            span.setLayer(spanObject.getSpanLayer().name());
            span.setType(spanObject.getSpanType().name());
            span.setEndpointName(spanObject.getOperationName());

            if (spanObject.getPeerId() == 0) {
                span.setPeer(spanObject.getPeer());
            } else {
                span.setPeer(getNetworkAddressInventoryCache().get(spanObject.getPeerId()).getName());
            }

            final ServiceInventory serviceInventory = getServiceInventoryCache().get(segmentObject.getServiceId());
            if (serviceInventory != null) {
                span.setServiceCode(serviceInventory.getName());
            } else {
                span.setServiceCode("unknown");
            }

            if (spanObject.getComponentId() == 0) {
                span.setComponent(spanObject.getComponent());
            } else {
                span.setComponent(getComponentLibraryCatalogService().getComponentName(spanObject.getComponentId()));
            }

            spanObject.getTagsList().forEach(tag -> {
                KeyValue keyValue = new KeyValue();
                keyValue.setKey(tag.getKey());
                keyValue.setValue(tag.getValue());
                span.getTags().add(keyValue);
            });

            spanObject.getLogsList().forEach(log -> {
                LogEntity logEntity = new LogEntity();
                logEntity.setTime(log.getTime());

                log.getDataList().forEach(data -> {
                    KeyValue keyValue = new KeyValue();
                    keyValue.setKey(data.getKey());
                    keyValue.setValue(data.getValue());
                    logEntity.getData().add(keyValue);
                });

                span.getLogs().add(logEntity);
            });

            spans.add(span);
        });

        return spans;
    }

}
