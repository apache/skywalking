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

package org.apache.skywalking.apm.collector.ui.query;

import java.text.ParseException;
import java.util.List;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.ResponseTimeTrend;
import org.apache.skywalking.apm.collector.storage.ui.common.ThroughputTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.ui.server.CPUTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.GCTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.MemoryTrend;
import org.apache.skywalking.apm.collector.ui.graphql.Query;
import org.apache.skywalking.apm.collector.ui.service.ServerService;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;

/**
 * @author peng-yongsheng
 */
public class ServerQuery implements Query {

    private final ModuleManager moduleManager;
    private ServerService serverService;

    public ServerQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private ServerService getServerService() {
        if (ObjectUtils.isEmpty(serverService)) {
            this.serverService = new ServerService(moduleManager);
        }
        return serverService;
    }

    public List<AppServerInfo> searchServer(String keyword, Duration duration) throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        return getServerService().searchServer(keyword, startSecondTimeBucket, endSecondTimeBucket);
    }

    public List<AppServerInfo> getAllServer(int applicationId, Duration duration) throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        return getServerService().getAllServer(applicationId, startSecondTimeBucket, endSecondTimeBucket);
    }

    public ResponseTimeTrend getServerResponseTimeTrend(int serverId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        return getServerService().getServerResponseTimeTrend(serverId, duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public ThroughputTrend getServerTPSTrend(int serverId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        return getServerService().getServerTPSTrend(serverId, duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public CPUTrend getCPUTrend(int serverId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        return getServerService().getCPUTrend(serverId, duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public GCTrend getGCTrend(int serverId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        return getServerService().getGCTrend(serverId, duration.getStep(), startTimeBucket, endTimeBucket);
    }

    public MemoryTrend getMemoryTrend(int serverId, Duration duration) throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        return getServerService().getMemoryTrend(serverId, duration.getStep(), startTimeBucket, endTimeBucket);
    }
}
