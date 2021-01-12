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
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.LogEntity;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.ProfileAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.ProfiledSegment;
import org.apache.skywalking.oap.server.core.query.type.ProfiledSpan;
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
    private NetworkAddressAliasCache networkAddressAliasCache;
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

    private IProfileTaskLogQueryDAO getProfileTaskLogQueryDAO() {
        if (isNull(profileTaskLogQueryDAO)) {
            profileTaskLogQueryDAO = moduleManager.find(StorageModule.NAME)
                                                  .provider()
                                                  .getService(IProfileTaskLogQueryDAO.class);
        }
        return profileTaskLogQueryDAO;
    }

    private IProfileThreadSnapshotQueryDAO getProfileThreadSnapshotQueryDAO() {
        if (isNull(profileThreadSnapshotQueryDAO)) {
            profileThreadSnapshotQueryDAO = moduleManager.find(StorageModule.NAME)
                                                         .provider()
                                                         .getService(IProfileThreadSnapshotQueryDAO.class);
        }
        return profileThreadSnapshotQueryDAO;
    }

    private NetworkAddressAliasCache getNetworkAddressAliasCache() {
        if (networkAddressAliasCache == null) {
            this.networkAddressAliasCache = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(NetworkAddressAliasCache.class);
        }
        return networkAddressAliasCache;
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
    public List<ProfileTask> getTaskList(String serviceId, String endpointName) throws IOException {
        final List<ProfileTask> tasks = getProfileTaskDAO().getTaskList(serviceId, endpointName, null, null, null);

        // query all and filter on task to match logs
        List<ProfileTaskLog> taskLogList = getProfileTaskLogQueryDAO().getTaskLogList();
        if (taskLogList == null) {
            taskLogList = Collections.emptyList();
        }

        // add service name
        if (CollectionUtils.isNotEmpty(tasks)) {

            for (ProfileTask task : tasks) {
                final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                    task.getServiceId());
                task.setServiceName(serviceIDDefinition.getName());

                // filter all task logs
                task.setLogs(taskLogList.stream().filter(l -> Objects.equal(l.getTaskId(), task.getId())).map(l -> {
                    // get instance name from cache
                    final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                        .analysisId(l.getInstanceId());
                    l.setInstanceName(instanceIDDefinition.getName());
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

            span.setPeer(spanObject.getPeer());

            span.setEndpointName(spanObject.getOperationName());

            span.setServiceCode(segmentObject.getService());
            span.setServiceInstanceName(segmentObject.getServiceInstance());

            span.setComponent(getComponentLibraryCatalogService().getComponentName(spanObject.getComponentId()));

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
